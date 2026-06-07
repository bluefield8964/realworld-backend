package realworld_backend.commerce.service.subscription.invoice;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.commerce.model.exception.WebhookDuplicateIgnoredException;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.PaymentStatus;
import realworld_backend.commerce.model.invoice.InvoiceWebhookEvent;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.service.AbnormalOrchestrator;
import realworld_backend.commerce.service.InvoiceService;
import realworld_backend.commerce.service.entitlement.EntitlementProjector;
import realworld_backend.commerce.service.statemachine.SubscriptionWebhookStateDecision;
import realworld_backend.commerce.service.statemachine.SubscriptionWebhookStateMachine;
import realworld_backend.commerce.service.statemachine.StateNature;
import realworld_backend.commerce.service.statemachine.TransitionClass;
import realworld_backend.commerce.service.subscription.CustomerSubscriptionService;
import realworld_backend.commerce.service.subscription.snapshot.SubscriptionSnapshotMergePolicy;
import realworld_backend.commerce.service.subscription.snapshot.SubscriptionSnapshotMergeService;
import realworld_backend.commerce.service.subscription.snapshot.SubscriptionSnapshotPatch;
import realworld_backend.commerce.service.webhook.PaymentFailureEscalationService;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.webhook.parser.WebhookObjectParserRouter;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionInvoiceWebhookService {
    private final WebhookObjectParserRouter webhookObjectParserRouter;
    private final RedissonClient redissonClient;
    private final AbnormalOrchestrator abnormalOrchestrator;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CustomerSubscriptionService customerSubscriptionService;
    private final InvoiceService invoiceService;
    private final PaymentFailureEscalationService paymentFailureEscalationService;
    private final SubscriptionWebhookStateMachine subscriptionWebhookStateMachine;
    private final SubscriptionSnapshotMergeService subscriptionSnapshotMergeService;
    private final EntitlementProjector entitlementProjector;

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
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }

        if (!event.isSubscriptionInvoice() || !event.isPaymentFailed()) {
            throw new BizException(ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE);
        }

        String invoiceId = resolveInvoiceId(invoiceObject);
        if (invoiceId == null || invoiceId.isBlank()) {
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
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
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }

        ctx.setTrackingId(businessOrderNo);
        ctx.setProviderTrackingId(invoiceId);
        Instant providerPeriodStart = invoiceObject.periodStartInstant();
        Instant providerPeriodEnd = invoiceObject.periodEndInstant();
        RLock lock = redissonClient.getLock("invoice:lock:" + invoiceId);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);
            //long lock
            if (idempotencyLock != null) {
                log.info("handleInvoicePaymentFailed: invoice already processed after lock, eventId={}, finish request", eventId);
                throw new WebhookDuplicateIgnoredException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }
            //short lock
            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("handleInvoicePaymentFailed: failed to acquire lock for invoice {}, finish request", invoiceId);
                throw new WebhookDuplicateIgnoredException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            boolean b = invoiceService.upsertInvoiceByEvent(event, businessOrderNo, PaymentStatus.FAILED);
            if (!b) {
                paymentFailureEscalationService.recordIncidentForException(
                        ctx,
                        null,
                        null,
                        "upsert invoice failed",
                        null,
                        null
                );
                throw new BizException(ErrorCode.INVOICE_INSERT_FAILED);
            }

            requireLocalSubscription(
                    businessOrderNo,
                    invoiceId,
                    eventId,
                    idempotentKey,
                    ctx,
                    "handleSubscription_CUSTOMER_SUBSCRIPTION_MISSING"
            );
            backfillSubscriptionSnapshotFromInvoice(businessOrderNo, invoiceObject);
            refreshEntitlementProjection(businessOrderNo);
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
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }
        if (!event.isSubscriptionInvoice() || !event.isPaymentSuccessful()) {
            throw new BizException(ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE);
        }
        String invoiceId = resolveInvoiceId(invoiceObject);
        if (invoiceId == null || invoiceId.isBlank()) {
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }

        String businessOrderNo = resolveInvoiceBusinessOrderNo(invoiceObject);
        if (businessOrderNo == null || businessOrderNo.isBlank()) {
            if (ctx.getAttempts() < 5) {
                throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
            }
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
                throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
            }
            businessOrderNo = customerSubscription.get().getSubscriptionNo();
        }

        ctx.setTrackingId(businessOrderNo);
        ctx.setProviderTrackingId(invoiceId);
        Instant providerPeriodStart = invoiceObject.periodStartInstant();
        Instant providerPeriodEnd = invoiceObject.periodEndInstant();
        RLock lock = redissonClient.getLock("invoice:lock:" + invoiceId);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);
            if (idempotencyLock != null) {
                log.info("handleInvoicePaymentSucceeded: invoice already processed after lock, eventId={}, finish request", eventId);
                throw new WebhookDuplicateIgnoredException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }
            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("handleInvoicePaymentSucceeded: Failed to acquire lock for invoice {}, finish request", invoiceId);
                throw new WebhookDuplicateIgnoredException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            boolean upserted = invoiceService.upsertInvoiceByEvent(event, businessOrderNo, PaymentStatus.SUCCESS);
            if (!upserted) {
                paymentFailureEscalationService.recordIncidentForException(
                        ctx,
                        null,
                        null,
                        "upsert invoice failed",
                        null,
                        null
                );
                throw new BizException(ErrorCode.INVOICE_INSERT_FAILED);
            }

            requireLocalSubscription(
                    businessOrderNo,
                    invoiceId,
                    eventId,
                    idempotentKey,
                    ctx,
                    "handleInvoice_CUSTOMER_SUBSCRIPTION_MISSING"
            );
            backfillSubscriptionSnapshotFromInvoice(businessOrderNo, invoiceObject);
            refreshEntitlementProjection(businessOrderNo);
            redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
            return;
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

    @Transactional
    public void handleInvoicePaymentActionRequired(WebhookContext ctx) {
        InvoiceWebhookEvent event =
                webhookObjectParserRouter.parseAs(
                        ctx.getProviderRawEvent(),
                        ctx.getEventType(),
                        InvoiceWebhookEvent.class
                );
        ctx.setProviderEvent(event);
        String eventId = ctx.getEventId();
        String idempotentKey = "handleInvoice:event:" + eventId;
        InvoiceWebhookEvent.InvoiceObject invoiceObject = event == null ? null : event.getObject();
        if (invoiceObject == null) {
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }
        if (!event.isSubscriptionInvoice()) {
            throw new BizException(ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE);
        }

        String invoiceId = resolveInvoiceId(invoiceObject);
        if (!hasText(invoiceId)) {
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }

        String businessOrderNo = resolveInvoiceBusinessOrderNo(invoiceObject);
        if (!hasText(businessOrderNo)) {
            businessOrderNo = findCustomerSubscription(invoiceObject)
                    .map(CustomerSubscription::getSubscriptionNo)
                    .orElse(null);
        }

        ctx.setProviderTrackingId(invoiceId);
        ctx.setTrackingId(businessOrderNo);
        if (!hasText(businessOrderNo)) {
            upsertSubscriptionMissingAbnormalOrder(null, invoiceId, eventId, idempotentKey, ctx);
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }

        if (customerSubscriptionService.findBySubscriptionNo(businessOrderNo).isEmpty()) {
            upsertSubscriptionMissingAbnormalOrder(businessOrderNo, invoiceId, eventId, idempotentKey, ctx);
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }

        RLock lock = redissonClient.getLock("invoice:lock:" + invoiceId);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);
            if (idempotencyLock != null) {
                log.info("handleInvoicePaymentActionRequired: invoice already processed after lock, eventId={}, finish request", eventId);
                throw new WebhookDuplicateIgnoredException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("handleInvoicePaymentActionRequired: Failed to acquire lock for invoice {}, finish request", invoiceId);
                throw new WebhookDuplicateIgnoredException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            boolean upserted = invoiceService.upsertInvoiceByEvent(event, businessOrderNo, PaymentStatus.PROCESSING);
            if (!upserted) {
                paymentFailureEscalationService.recordIncidentForException(
                        ctx,
                        null,
                        null,
                        "upsert invoice failed",
                        null,
                        null
                );
                throw new BizException(ErrorCode.INVOICE_INSERT_FAILED);
            }
            backfillSubscriptionSnapshotFromInvoice(businessOrderNo, invoiceObject);
            redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
            log.info(
                    "handleInvoicePaymentActionRequired: invoice snapshot stored, subscriptionNo={}, invoiceId={}",
                    businessOrderNo,
                    invoiceId
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("handleInvoicePaymentActionRequired: Interrupted while acquiring lock, waiting for reconcile, invoiceId={}", invoiceId);
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

    private Optional<CustomerSubscription> findCustomerSubscription(
            InvoiceWebhookEvent.InvoiceObject invoiceObject
    ) {
        return Optional.ofNullable(invoiceObject.getSubscription())
                .flatMap(customerSubscriptionService::findByProviderSubscriptionId)
                .or(() -> Optional.ofNullable(invoiceObject.getCustomer())
                        .flatMap(customerSubscriptionService::findByProviderCustomerId));
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

    private void backfillSubscriptionSnapshotFromInvoice(
            String subscriptionNo,
            InvoiceWebhookEvent.InvoiceObject invoiceObject
    ) {
        if (!hasText(subscriptionNo) || invoiceObject == null) {
            return;
        }
        Optional<CustomerSubscription> localOptional = customerSubscriptionService.findBySubscriptionNo(subscriptionNo);
        if (localOptional.isEmpty()) {
            return;
        }
        CustomerSubscription local = localOptional.get();
        SubscriptionSnapshotPatch patch = SubscriptionSnapshotPatch.builder()
                .providerSubscriptionId(invoiceObject.getSubscription())
                .providerCustomerId(invoiceObject.getCustomer())
                .currentPeriodStart(invoiceObject.periodStartUtc())
                .currentPeriodEnd(invoiceObject.periodEndUtc())
                .build();
        boolean changed = subscriptionSnapshotMergeService.apply(
                local,
                patch,
                SubscriptionSnapshotMergePolicy.invoiceConservative()
        );

        if (changed) {
            local.setUpdatedAt(UtcTimeMapper.nowUtc());
            customerSubscriptionService.save(local);
        }
    }

    private void refreshEntitlementProjection(String subscriptionNo) {
        customerSubscriptionService.findBySubscriptionNo(subscriptionNo)
                .ifPresent(subscription ->
                        entitlementProjector.refreshSubscriptionEntitlement(subscription, UtcTimeMapper.nowUtc()));
    }

    private CustomerSubscription requireLocalSubscription(
            String subscriptionNo,
            String providerObjectId,
            String eventId,
            String idempotentKey,
            WebhookContext ctx,
            String source
    ) {
        return customerSubscriptionService.findBySubscriptionNo(subscriptionNo)
                .orElseThrow(() -> {
                    abnormalOrchestrator.upsertAbnormalOrder(
                            subscriptionNo,
                            eventId,
                            ctx.getEventType(),
                            providerObjectId,
                            source,
                            "CUSTOMER_SUBSCRIPTION_MISSING",
                            AbnormalOrderType.SUBSCRIPTION_MISSING,
                            ctx.getProvider(),
                            null
                    );
                    ctx.setAbnormalAlreadyUpserted(true);
                    redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                    return new BizException(ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND);
                });
    }

    private boolean applySubscriptionTransitionDecision(
            String subscriptionNo,
            SubscriptionWebhookStateDecision decision,
            Instant providerPeriodStart,
            Instant providerPeriodEnd,
            Boolean cancelAtPeriodEnd,
            Instant now
    ) {
        boolean clearActiveKey = decision.nextStateNature() == StateNature.TERMINAL_STATE;
        return customerSubscriptionService.updateFromProviderIfStatusChanged(
                subscriptionNo,
                List.of(decision.currentStatus()),
                decision.nextStatus(),
                providerPeriodStart,
                providerPeriodEnd,
                cancelAtPeriodEnd,
                clearActiveKey,
                now
        ) == 1;
    }

    private void handleSubscriptionIllegalDecision(
            WebhookContext ctx,
            SubscriptionWebhookStateDecision decision,
            String idempotentKey
    ) {
        if (decision.transitionClass() == TransitionClass.RETRYABLE_ILLEGAL_TRANSITION) {
            log.warn(
                    "subscription transition is retryable-illegal, eventType={}, eventId={}, trackingId={}, reason={}",
                    ctx.getEventType(),
                    ctx.getEventId(),
                    ctx.getTrackingId(),
                    decision.reason()
            );
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        if (decision.transitionClass() == TransitionClass.ILLEGAL_TRANSITION) {
            log.warn(
                    "subscription transition is illegal, eventType={}, eventId={}, trackingId={}, reason={}",
                    ctx.getEventType(),
                    ctx.getEventId(),
                    ctx.getTrackingId(),
                    decision.reason()
            );
            paymentFailureEscalationService.recordIncidentForException(
                    ctx,
                    null,
                    null,
                    decision.reason(),
                    null,
                    null
            );
            redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
            throw new BizException(ErrorCode.SUBSCRIPTION_STATUS_CHANGE_FAIL);
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
