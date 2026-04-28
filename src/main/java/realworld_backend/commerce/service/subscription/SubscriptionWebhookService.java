package realworld_backend.commerce.service.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.model.PaymentStatus;
import realworld_backend.commerce.model.invoice.InvoiceWebhookEvent;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.SubscriptionHistory;
import realworld_backend.commerce.model.subscription.SubscriptionWebhookEvent;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.service.AbnormalOrchestrator;
import realworld_backend.commerce.service.InvoiceService;
import realworld_backend.commerce.service.core.WebhookContext;
import realworld_backend.commerce.service.core.ProviderTimeMapper;
import realworld_backend.commerce.service.impl.parser.WebhookObjectParserRouter;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor

public class SubscriptionWebhookService {
    private final WebhookObjectParserRouter webhookObjectParserRouter;
    private final RedissonClient redissonClient;
    private final SubscriptionHistoryService subscriptionHistoryService;
    private final AbnormalOrchestrator abnormalOrchestrator;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CustomerSubscriptionService customerSubscriptionService;
    private final InvoiceService invoiceService;

    //subscription
    @Transactional
    public void handleSubscriptionDeletedEvent(WebhookContext ctx) {
        SubscriptionWebhookEvent event =
                webhookObjectParserRouter.parseAs(
                        ctx.getProviderRawEvent(),
                        ctx.getEventType(),
                        SubscriptionWebhookEvent.class
                );
        String eventId = ctx.getEventId();
        String idempotentKey = "handleSubscription:event:" + eventId;
        SubscriptionWebhookEvent.SubscriptionObject subscriptionObject = event.getObject();

        if (subscriptionObject == null) {
            throw new BizException(ErrorCode.SUBSCRIPTION_SESSION_NOT_FOUND);
        }
        Instant providerPeriodStart = subscriptionObject.currentPeriodStartInstant();
        Instant providerPeriodEnd = subscriptionObject.currentPeriodEndInstant();
        Instant now = Instant.now();

        String subscriptionNo = resolveSubscriptionNo(subscriptionObject);
        if (subscriptionNo == null || subscriptionNo.isBlank()) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }
        ctx.setTrackingId(subscriptionNo);
        ctx.setProviderTrackingId(subscriptionObject.getId());
        //distributed lock
        RLock lock = redissonClient.getLock("subscription:lock:" + subscriptionNo);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);

            if (idempotencyLock != null) {
                // Event-level idempotency hit.
                log.info("handleSubscriptionDeletedEvent: subscription already processed after lock, eventId={}, finish request", eventId);
                return;
            }

            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("handleSubscriptionDeletedEvent: failed to acquire lock for subscription {}, finish request", subscriptionNo);
                // Lock contention is treated as retryable.
                throw new BizException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }
            //is it necessary to restore subscription duration ?
            //db CAS operation
            int isUpdated = customerSubscriptionService.updateFromProviderIfStatusChanged
                    (subscriptionNo,
                            SubscriptionStatus.ACTIVE,
                            SubscriptionStatus.CANCELED,
                            providerPeriodStart,
                            providerPeriodEnd,
                            subscriptionObject.getCancelAtPeriodEnd(),
                            now);
            if (isUpdated == 1) {
                log.info("handleSubscriptionDeletedEvent: subscription cancellation applied, subscriptionNo={}", subscriptionNo);
                SubscriptionHistoryService.AppendResult appendResult =
                        subscriptionHistoryService.appendCanceledEventIfLatestPaid(subscriptionNo);

                if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.HISTORY_MISSING) {
                    Optional<SubscriptionHistory> bySessionId = subscriptionHistoryService.findBySubscription_subscriptionNo(subscriptionNo);
                    if (bySessionId.isEmpty()) {
                        abnormalOrchestrator.upsertAbnormalOrder(
                                subscriptionNo,
                                eventId,
                                BusinessEventType.SUBSCRIPTION_HISTORY_MISSING,
                                subscriptionObject.getId(),
                                "handleSubscriptionDeleted_fail_subscriptionHistory",
                                "subscriptionHistory_change_to_INITIAL_FAIL",
                                AbnormalOrderType.SUBSCRIPTION_MISSING,//does abnormal subscription should be categorized into AbnormalOrder?
                                ctx.getProvider(),
                                null
                        );
                        ctx.setAbnormalAlreadyUpserted(true);
                        // Set idempotent key to avoid webhook storm while abnormal flow takes over.
                        redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                        throw new BizException(ErrorCode.SUBSCRIPTION_HISTORY_NOT_FOUND);
                    }
                }
                if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.LATEST_PAYMENT_STATUS_MISMATCH) {
                    log.warn("append canceled history skipped due to latest payment status mismatch, subscriptionNo={}", subscriptionNo);
                }
                // update=0 with existing payment is treated as idempotent success.
                backfillProviderIdentityAndDeleteMeta(subscriptionNo, subscriptionObject, true);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }
            //if this CustomerSubscription doesn't exist
            Optional<CustomerSubscription> bySubscriptionNo = customerSubscriptionService.findBySubscriptionNo(subscriptionNo);
            if (bySubscriptionNo.isEmpty()) {
                abnormalOrchestrator.upsertAbnormalOrder(
                        subscriptionNo,
                        eventId,
                        BusinessEventType.CUSTOMER_SUBSCRIPTION_MISSING,
                        subscriptionObject.getId(),
                        "handleSubscriptionDeleted_CUSTOMER_SUBSCRIPTION_MISSING",
                        "CUSTOMER_SUBSCRIPTION_MISSING",
                        AbnormalOrderType.SUBSCRIPTION_MISSING,//is abnormal Subscription should be categorized into abnormal order?
                        ctx.getProvider(),
                        null
                );
                ctx.setAbnormalAlreadyUpserted(true);
                // Missing subscription is handed over to abnormal reconciliation.
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                throw new BizException(ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND);
            }

            backfillProviderIdentityAndDeleteMeta(subscriptionNo, subscriptionObject, true);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("handleSubscriptionDeletedEvent: interrupted while acquiring lock, waiting for reconcile, subscriptionNo={}", subscriptionNo);
            throw new BizException(ErrorCode.LOCK_INTERRUPTED);

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


    //subscription json verification
    @Transactional
    public void handleSubscriptionCreatedEvent(WebhookContext ctx) {
        SubscriptionWebhookEvent event =
                webhookObjectParserRouter.parseAs(
                        ctx.getProviderRawEvent(),
                        ctx.getEventType(),
                        SubscriptionWebhookEvent.class
                );
        ctx.setProviderEvent(event);
        SubscriptionWebhookEvent.SubscriptionObject subscriptionObject = event.getObject();
        if (subscriptionObject == null) {
            throw new BizException(ErrorCode.SUBSCRIPTION_SESSION_NOT_FOUND);
        }
        String providerSubscriptionId = subscriptionObject.getId();

        Map<String, String> metadata = subscriptionObject.getMetadata();
        String userId = metadata == null ? null : metadata.get("userId");
        String subscriptionNo = resolveSubscriptionNo(subscriptionObject);
        String product = metadata == null ? null : metadata.get("product");
        ctx.setTrackingId(subscriptionNo); // Subscription event uses subscriptionNo as trackingId
        ctx.setProviderTrackingId(providerSubscriptionId);
        if (subscriptionNo == null || subscriptionNo.isBlank()) {
            log.error("subscription mapping missing: subscriptionNo, providerSubscriptionId={}", providerSubscriptionId);
            throw new BizException(ErrorCode.JSON_ERROR);
        }

        if (userId == null || userId.isBlank()) {
            log.warn("subscription metadata missing userId, fallback mapping by providerSubscriptionId, subscriptionNo={}, providerSubscriptionId={}",
                    subscriptionNo, providerSubscriptionId);
        }

        log.info(
                "Subscription created event accepted: subscriptionNo={}, product={}, providerSubscriptionId={}",
                subscriptionNo,
                product,
                providerSubscriptionId
        );

        // Final transition is protected by event/session idempotency gates.
        activateSubscriptionFromWebhook(ctx);

    }

    public void activateSubscriptionFromWebhook(WebhookContext ctx) {
        SubscriptionWebhookEvent providerEvent = (SubscriptionWebhookEvent) ctx.getProviderEvent();
        String eventId = ctx.getEventId();
        String subscriptionNo = ctx.getTrackingId();
        if (subscriptionNo == null || subscriptionNo.isBlank()) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }
        String idempotentKey = "handleSubscription:event:" + eventId;
        SubscriptionWebhookEvent.SubscriptionObject subscriptionObject = providerEvent.getObject();
        Instant providerPeriodStart = subscriptionObject.currentPeriodStartInstant();
        Instant providerPeriodEnd = subscriptionObject.currentPeriodEndInstant();
        Instant now = Instant.now();

        //distributed lock
        RLock lock = redissonClient.getLock("subscription:lock:" + subscriptionNo);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);

            if (idempotencyLock != null) {
                // Event-level idempotency hit.
                log.info("activateSubscriptionFromWebhook: subscription already processed after lock, eventId={}, finish request", eventId);
                return;
            }

            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("Failed to acquire lock for subscription {}, finish request", subscriptionNo);
                // Lock contention is treated as retryable.
                throw new BizException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            // db CAS operation:
            // allow out-of-order events by accepting both CREATED and PENDING as source states.
            int isUpdated = customerSubscriptionService.updateFromProviderIfStatusChanged(
                    subscriptionNo,
                    SubscriptionStatus.PENDING,
                    SubscriptionStatus.ACTIVE,
                    providerPeriodStart,
                    providerPeriodEnd,
                    subscriptionObject.getCancelAtPeriodEnd(),
                    now
            );
            if (isUpdated == 0) {
                isUpdated = customerSubscriptionService.updateFromProviderIfStatusChanged(
                        subscriptionNo,
                        SubscriptionStatus.CREATED,
                        SubscriptionStatus.ACTIVE,
                        providerPeriodStart,
                        providerPeriodEnd,
                        subscriptionObject.getCancelAtPeriodEnd(),
                        now
                );
            }
            if (isUpdated == 1) {
                log.info("activateSubscriptionFromWebhook: subscription activation applied, subscriptionNo={}", subscriptionNo);
                SubscriptionHistoryService.AppendResult appendResult =
                        subscriptionHistoryService.appendActiveEventIfLatestPaid(subscriptionNo);

                if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.HISTORY_MISSING) {
                    Optional<SubscriptionHistory> bySessionId = subscriptionHistoryService.findBySubscription_subscriptionNo(subscriptionNo);
                    if (bySessionId.isEmpty()) {
                        abnormalOrchestrator.upsertAbnormalOrder(
                                subscriptionNo,
                                eventId,
                                BusinessEventType.SUBSCRIPTION_HISTORY_MISSING,
                                subscriptionObject.getId(),
                                "handleSubscription_fail_subscriptionHistory_missing",
                                "subscriptionHistory_missing",
                                AbnormalOrderType.PAYMENT_MISSING,//does abnormal subscription should be categorized into AbnormalOrder?
                                providerEvent.getProvider(),
                                null
                        );
                        ctx.setAbnormalAlreadyUpserted(true);
                        // Set idempotent key to avoid webhook storm while abnormal flow takes over.
                        redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                        throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
                    }
                }
                if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.LATEST_PAYMENT_STATUS_MISMATCH) {
                    log.warn("append active history skipped due to latest payment status mismatch, subscriptionNo={}", subscriptionNo);
                }
                // update=0 with existing payment is treated as idempotent success.
                backfillProviderIdentityAndDeleteMeta(subscriptionNo, subscriptionObject, false);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }

            //if this CustomerSubscription doesn't exist
            Optional<CustomerSubscription> bySubscriptionNo = customerSubscriptionService.findBySubscriptionNo(subscriptionNo);
            if (bySubscriptionNo.isEmpty()) {
                abnormalOrchestrator.upsertAbnormalOrder(
                        subscriptionNo,
                        eventId,
                        BusinessEventType.SUBSCRIPTION_MISSING,
                        subscriptionObject.getId(),
                        "handleSubscription_CUSTOMER_SUBSCRIPTION_MISSING",
                        "CUSTOMER_SUBSCRIPTION_MISSING",
                        AbnormalOrderType.SUBSCRIPTION_MISSING,//is abnormal Subscription should be categorized into abnormal order?
                        ctx.getProvider(),
                        null
                );
                ctx.setAbnormalAlreadyUpserted(true);
                // Missing order is handed over to abnormal reconciliation.
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                throw new BizException(ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND);
            }

            // update=0 with existing payment is treated as idempotent success.
            backfillProviderIdentityAndDeleteMeta(subscriptionNo, subscriptionObject, false);
            redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while acquiring lock, waiting for reconcile, subscriptionNo={}", subscriptionNo);
            throw new BizException(ErrorCode.LOCK_INTERRUPTED);

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


    //invoice
    @Transactional
    public void handleInvoicePaymentFailed(WebhookContext ctx) {
        InvoiceWebhookEvent event =
                webhookObjectParserRouter.parseAs(
                        ctx.getProviderRawEvent(),
                        ctx.getEventType(),
                        InvoiceWebhookEvent.class
                );

        ctx.setProviderEvent(event);

        String eventId = ctx.getEventId();
        String idempotentKey = "handleInvoice:event:" + eventId;

        InvoiceWebhookEvent.InvoiceObject invoiceObject = event.getObject();
        if (invoiceObject == null) {
            throw new BizException(ErrorCode.INVOICE_SESSION_NOT_FOUND);
        }

        if (!event.isSubscriptionInvoice() || !event.isPaymentFailed()) {
            throw new BizException(ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE);
        }

        String invoiceId = resolveInvoiceId(invoiceObject);
        if (invoiceId == null || invoiceId.isBlank()) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }

        String businessOrderNo = resolveInvoiceBusinessOrderNo(invoiceObject);
        if (ctx.getAttempts() > 4 && businessOrderNo == null) {
            CustomerSubscription invoiceSubscription =
                    findCustomerSubscription(invoiceObject)
                            .orElseThrow(() -> {
                                upsertSubscriptionMissingAbnormalOrder(
                                        null,
                                        invoiceId,
                                        eventId,
                                        idempotentKey,
                                        ctx
                                );
                                return new BizException(ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND);
                            });

            businessOrderNo = invoiceSubscription.getSubscriptionNo();

        } else if (businessOrderNo == null || businessOrderNo.isBlank()) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }

        ctx.setTrackingId(businessOrderNo);
        ctx.setProviderTrackingId(invoiceId);
        //distributed lock
        RLock lock = redissonClient.getLock("invoice:lock:" + invoiceId);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);

            if (idempotencyLock != null) {
                // Event-level idempotency hit.
                log.info("handleInvoicePaymentFailed: invoice already processed after lock, eventId={}, finish request", eventId);
                return;
            }

            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("handleInvoicePaymentFailed: failed to acquire lock for invoice {}, finish request", invoiceId);
                // Lock contention is treated as retryable.
                throw new BizException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            // Write failed invoice accounting record.
            invoiceService.upsertInvoiceByEvent(event, businessOrderNo, PaymentStatus.FAILED);
            customerSubscriptionService.updateStatusToPastDue(
                    businessOrderNo,
                    Instant.now()
            );

            // Subscription existence check remains for reconcile signal.
            Optional<CustomerSubscription> bySubscriptionNo = customerSubscriptionService.findBySubscriptionNo(businessOrderNo);
            if (bySubscriptionNo.isEmpty()) {
                abnormalOrchestrator.upsertAbnormalOrder(
                        businessOrderNo,
                        eventId,
                        ctx.getEventType(),
                        invoiceId,
                        "handleSubscription_CUSTOMER_SUBSCRIPTION_MISSING",
                        "CUSTOMER_SUBSCRIPTION_MISSING",
                        AbnormalOrderType.SUBSCRIPTION_MISSING,//is abnormal Subscription should be categorized into abnormal order?
                        ctx.getProvider(),
                        null
                );
                ctx.setAbnormalAlreadyUpserted(true);
                // Missing subscription is handed over to abnormal reconciliation.
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                throw new BizException(ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND);
            }

            redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("handleInvoicePaymentFailed: Interrupted while acquiring lock, waiting for reconcile, invoiceId={}", invoiceId);
            throw new BizException(ErrorCode.LOCK_INTERRUPTED);

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    //invoice
    @Transactional
    public void handleInvoicePaymentSucceeded(WebhookContext ctx) {
        InvoiceWebhookEvent event =
                webhookObjectParserRouter.parseAs(
                        ctx.getProviderRawEvent(),
                        ctx.getEventType(),
                        InvoiceWebhookEvent.class
                );
        ctx.setProviderEvent(event);
        String eventId = ctx.getEventId();
        String idempotentKey = "handleInvoice:event:" + eventId;
        InvoiceWebhookEvent.InvoiceObject invoiceObject = event.getObject();
        if (invoiceObject == null) {
            throw new BizException(ErrorCode.INVOICE_SESSION_NOT_FOUND);
        }
        if (!event.isSubscriptionInvoice() || !event.isPaymentSuccessful()) {
            throw new BizException(ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE);
        }
        String invoiceId = resolveInvoiceId(invoiceObject);
        if (invoiceId == null || invoiceId.isBlank()) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }

        String businessOrderNo = resolveInvoiceBusinessOrderNo(invoiceObject);
        if (businessOrderNo == null || businessOrderNo.isBlank()) {
            if (ctx.getAttempts() < 5) {
                throw new BizException(ErrorCode.JSON_ERROR);
            }
            // if failed too many times, retrieve mapping by provider IDs
            Optional<CustomerSubscription> customerSubscription = Optional.empty();

            if (invoiceObject.getSubscription() != null)
                customerSubscription = customerSubscriptionService.findByProviderSubscriptionId(invoiceObject.getSubscription());
            if (customerSubscription.isEmpty() && invoiceObject.getCustomer() != null)
                customerSubscription = customerSubscriptionService.findByProviderCustomerId(invoiceObject.getCustomer());
            if (customerSubscription.isEmpty()) {
                abnormalOrchestrator.upsertAbnormalOrder(
                        businessOrderNo,
                        eventId,
                        ctx.getEventType(),
                        invoiceObject.getId(),
                        "handleInvoice_CUSTOMER_SUBSCRIPTION_MISSING",
                        "CUSTOMER_SUBSCRIPTION_MISSING",
                        AbnormalOrderType.SUBSCRIPTION_MISSING,
                        ctx.getProvider(),
                        null
                );
                ctx.setAbnormalAlreadyUpserted(true);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                throw new BizException(ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND);
            }
            businessOrderNo = customerSubscription.get().getSubscriptionNo();
        }

        ctx.setTrackingId(businessOrderNo);
        ctx.setProviderTrackingId(invoiceId);
        RLock lock = redissonClient.getLock("invoice:lock:" + invoiceId);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);
            if (idempotencyLock != null) {
                log.info("handleInvoicePaymentSucceeded: invoice already processed after lock, eventId={}, finish request", eventId);
                return;
            }
            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("handleInvoicePaymentSucceeded: Failed to acquire lock for invoice {}, finish request", invoiceId);
                throw new BizException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            // Write succeeded invoice accounting record.
            //in this state, restore invoice first . i will utilize it latterly
            invoiceService.upsertInvoiceByEvent(event, businessOrderNo, PaymentStatus.SUCCESS);
            customerSubscriptionService.updateStatusToActiveFromRecoverable(
                    businessOrderNo,
                    Instant.now()
            );

           /* Optional<CustomerSubscription> bySubscriptionNo = customerSubscriptionRepository.findBySubscriptionNo(subscriptionNo);
            if (bySubscriptionNo.isEmpty()) {
                abnormalOrchestrator.upsertAbnormalOrder(
                        subscriptionNo,
                        eventId,
                        BusinessEventType.CUSTOMER_SUBSCRIPTION_MISSING,
                        subscriptionNo,
                        "handleInvoice_CUSTOMER_SUBSCRIPTION_MISSING",
                        "CUSTOMER_SUBSCRIPTION_MISSING",
                        AbnormalOrderType.SUBSCRIPTION_MISSING,
                        ctx.getProvider(),
                        null
                );
                ctx.setAbnormalAlreadyUpserted(true);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                throw new BizException(ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND);
            }*/
            redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("handleInvoicePaymentSucceeded: Interrupted while acquiring lock, waiting for reconcile, invoiceId={}", invoiceId);
            throw new BizException(ErrorCode.LOCK_INTERRUPTED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


    private String resolveInvoiceBusinessOrderNo(InvoiceWebhookEvent.InvoiceObject invoiceObject) {
        if (invoiceObject == null) {
            return null;
        }

        return invoiceObject.resolveMetadataValue("subscriptionNo");
    }

    private String resolveInvoiceId(InvoiceWebhookEvent.InvoiceObject invoiceObject) {
        if (invoiceObject == null) {
            return null;
        }
        return invoiceObject.getId();
    }

    private String resolveSubscriptionNo(SubscriptionWebhookEvent.SubscriptionObject subscriptionObject) {
        if (subscriptionObject == null) {
            return null;
        }

        Map<String, String> metadata = subscriptionObject.getMetadata();
        String subscriptionNo = metadata == null ? null : metadata.get("subscriptionNo");
        if (subscriptionNo != null && !subscriptionNo.isBlank()) {
            return subscriptionNo;
        }

        return findCustomerSubscription(subscriptionObject)
                .map(CustomerSubscription::getSubscriptionNo)
                .orElse(null);
    }

    private Optional<CustomerSubscription> findCustomerSubscription(
            InvoiceWebhookEvent.InvoiceObject invoiceObject
    ) {
        return Optional.ofNullable(invoiceObject.getSubscription())
                .flatMap(customerSubscriptionService::findByProviderSubscriptionId)
                .or(() -> Optional.ofNullable(invoiceObject.getCustomer())
                        .flatMap(customerSubscriptionService::findByProviderCustomerId));
    }

    private Optional<CustomerSubscription> findCustomerSubscription(
            SubscriptionWebhookEvent.SubscriptionObject subscriptionObject
    ) {
        if (subscriptionObject == null) {
            return Optional.empty();
        }

        String providerSubscriptionId = subscriptionObject.getId();
        if (providerSubscriptionId == null || providerSubscriptionId.isBlank()) {
            return Optional.empty();
        }

        return customerSubscriptionService.findByProviderSubscriptionId(providerSubscriptionId);
    }

    private void upsertSubscriptionMissingAbnormalOrder(
            String subscriptionNo,
            String providerObjectId,
            String eventId,
            String idempotentKey,
            WebhookContext ctx
    ) {
        abnormalOrchestrator.upsertAbnormalOrder(
                subscriptionNo,
                eventId,
                ctx.getEventType(),
                providerObjectId,
                "handleInvoice_CUSTOMER_SUBSCRIPTION_MISSING",
                "CUSTOMER_SUBSCRIPTION_MISSING",
                AbnormalOrderType.SUBSCRIPTION_MISSING,
                ctx.getProvider(),
                null
        );

        ctx.setAbnormalAlreadyUpserted(true);

        redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
    }

    private void backfillProviderIdentityAndDeleteMeta(
            String subscriptionNo,
            SubscriptionWebhookEvent.SubscriptionObject subscriptionObject,
            boolean deletedEvent
    ) {
        Optional<CustomerSubscription> localOptional = customerSubscriptionService.findBySubscriptionNo(subscriptionNo);
        if (localOptional.isEmpty()) {
            return;
        }

        CustomerSubscription local = localOptional.get();
        boolean changed = false;

        String providerSubscriptionId = subscriptionObject.getId();
        if (providerSubscriptionId != null
                && !providerSubscriptionId.isBlank()
                && !providerSubscriptionId.equals(local.getProviderSubscriptionId())) {
            local.setProviderSubscriptionId(providerSubscriptionId);
            changed = true;
        }

        String providerCustomerId = subscriptionObject.getCustomer();
        if (providerCustomerId != null
                && !providerCustomerId.isBlank()
                && !providerCustomerId.equals(local.getProviderCustomerId())) {
            local.setProviderCustomerId(providerCustomerId);
            changed = true;
        }

        if (deletedEvent) {
            LocalDateTime providerCanceledAt = ProviderTimeMapper.toUtcLocalDateTime(
                    ProviderTimeMapper.toInstant(subscriptionObject.getCanceledAt())
            );
            if (providerCanceledAt == null) {
                providerCanceledAt = LocalDateTime.now();
            }
            if (local.getCanceledAt() == null || !local.getCanceledAt().equals(providerCanceledAt)) {
                local.setCanceledAt(providerCanceledAt);
                changed = true;
            }
        }

        if (changed) {
            local.setUpdatedAt(LocalDateTime.now());
            customerSubscriptionService.save(local);
        }
    }
}



