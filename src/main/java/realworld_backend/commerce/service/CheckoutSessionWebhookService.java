package realworld_backend.commerce.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.Order;
import realworld_backend.commerce.model.OrderStatus;
import realworld_backend.commerce.model.Payment;
import realworld_backend.commerce.model.PaymentStatus;
import realworld_backend.commerce.model.checkoutPayment.CheckoutSessionWebhookEvent;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.SubscriptionHistory;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.repository.OrderRepository;
import realworld_backend.commerce.repository.PaymentRepository;
import realworld_backend.commerce.service.core.WebhookContext;
import realworld_backend.commerce.service.impl.parser.WebhookObjectParserRouter;
import realworld_backend.commerce.service.subscription.CustomerSubscriptionService;
import realworld_backend.commerce.service.subscription.SubscriptionHistoryService;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutSessionWebhookService {
    private final AbnormalOrchestrator abnormalOrchestrator;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final WebhookObjectParserRouter webhookObjectParserRouter;
    private final PaymentService paymentService;
    private final CustomerSubscriptionService customerSubscriptionService;
    private final SubscriptionHistoryService subscriptionHistoryService;

    public void handleCheckoutSessionCompleted(WebhookContext ctx) throws Exception {
        CheckoutSessionWebhookEvent event = CheckoutSessionWebhookEvent.parseProviderRawEvent(ctx.getProviderRawEvent());
        CheckoutSessionWebhookEvent.CheckoutSessionObject session = event.getObject();
        ctx.setProviderEvent(event);
        if (session == null) {
            throw new BizException(ErrorCode.STRIPE_SESSION_NOT_FOUND);
        }

        String mode = session.getMode();
        if ("payment".equals(mode)) {
            handleOrderPaymentCompleted(ctx);
            return;
        }

        if ("subscription".equals(mode)) {
            handleSubscriptionCheckoutCompletedEvent(ctx, session);
            return;
        }

        throw new BizException(ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE);
    }


    public void handleCheckoutSessionAsyncPaymentFailed(WebhookContext ctx) throws Exception {
        CheckoutSessionWebhookEvent event = CheckoutSessionWebhookEvent.parseProviderRawEvent(ctx.getProviderRawEvent());
        CheckoutSessionWebhookEvent.CheckoutSessionObject session = event.getObject();
        ctx.setProviderEvent(event);
        if (session == null) {
            throw new BizException(ErrorCode.STRIPE_SESSION_NOT_FOUND);
        }

        String mode = session.getMode();
        if ("payment".equals(mode)) {
            handleOrderPaymentFailed(ctx);
            return;
        }

        if ("subscription".equals(mode)) {
            handleSubscriptionCheckoutFailedEvent(ctx, session);
            return;
        }

        throw new BizException(ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE);
    }

    private void handleSubscriptionCheckoutCompletedEvent(WebhookContext ctx, CheckoutSessionWebhookEvent.CheckoutSessionObject session) {
        String eventId = ctx.getEventId();
        String idempotentKey = "handleSubscription:event:" + eventId;
        Map<String, String> metadata = session.getMetadata();
        String subscriptionNo = metadata == null ? null : metadata.get("subscriptionNo");
        if (subscriptionNo == null || subscriptionNo.isBlank()) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }
        ctx.setTrackingId(subscriptionNo);
        ctx.setProviderTrackingId(session.getId());
        log.info("handleSubscriptionCheckoutCompletedEvent: (subscription) accepted, eventId={}, trackingId={}",
                ctx.getEventId(), ctx.getTrackingId());

        Instant now = Instant.now();
        RLock lock = redissonClient.getLock("subscription:lock:" + subscriptionNo);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);

            if (idempotencyLock != null) {
                // Event-level idempotency hit.
                log.info("handleSubscriptionCheckoutCompletedEvent: subscription already processed after lock, eventId={}, finish request", eventId);
                return;
            }

            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("handleSubscriptionCheckoutCompletedEvent: Failed to acquire lock for subscription {}, finish request", subscriptionNo);
                // Lock contention is treated as retryable.
                throw new BizException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }
            //db CAS operation
            int isUpdated = customerSubscriptionService.updateFromProviderIfStatusChanged
                    (subscriptionNo,
                            SubscriptionStatus.CREATED,
                            SubscriptionStatus.PENDING,
                            null,
                            null,
                            null,
                            now);

            if (isUpdated == 1) {
                log.info("handleSubscriptionCheckoutCompletedEvent: subscription moved to pending, subscriptionNo={}", subscriptionNo);
                //
                SubscriptionHistoryService.AppendResult appendResult =
                        subscriptionHistoryService.appendPendingEventIfLatestInit(subscriptionNo);

                if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.HISTORY_MISSING) {
                    Optional<SubscriptionHistory> bySessionId = subscriptionHistoryService.findBySubscription_subscriptionNo(subscriptionNo);
                    if (bySessionId.isEmpty()) {
                        abnormalOrchestrator.upsertAbnormalOrder(
                                subscriptionNo,
                                eventId,
                                BusinessEventType.SUBSCRIPTION_HISTORY_MISSING,
                                session.getId(),
                                "handleSubscriptionCheckout_fail_subscriptionHistory",
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
                    log.warn("append pending history skipped due to latest payment status mismatch, subscriptionNo={}", subscriptionNo);
                }
                // update=0 with existing payment is treated as idempotent success.
                backfillSubscriptionProviderIdentity(subscriptionNo, session);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }

            Optional<CustomerSubscription> bySubscriptionNo = customerSubscriptionService.findBySubscriptionNo(subscriptionNo);
            if (bySubscriptionNo.isEmpty()) {
                abnormalOrchestrator.upsertAbnormalOrder(
                        subscriptionNo,
                        eventId,
                        BusinessEventType.CUSTOMER_SUBSCRIPTION_MISSING,
                        session.getId(),
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
            backfillSubscriptionProviderIdentity(subscriptionNo, session);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("handleSubscriptionCheckoutCompletedEvent:Interrupted while acquiring lock, waiting for reconcile, subscriptionNo={}", subscriptionNo);
            throw new BizException(ErrorCode.LOCK_INTERRUPTED);

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


    private void handleSubscriptionCheckoutFailedEvent(
            WebhookContext ctx,
            CheckoutSessionWebhookEvent.CheckoutSessionObject session
    ) {
        String eventId = ctx.getEventId();
        String idempotentKey = "handleSubscription:event:" + eventId;
        Map<String, String> metadata = session.getMetadata();
        String subscriptionNo = metadata == null ? null : metadata.get("subscriptionNo");
        if (subscriptionNo == null || subscriptionNo.isBlank()) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }
        ctx.setTrackingId(subscriptionNo);
        ctx.setProviderTrackingId(session.getId());
        log.info(
                "handleSubscriptionCheckoutFailedEvent: (subscription) accepted, eventId={}, trackingId={}",
                ctx.getEventId(),
                ctx.getTrackingId()
        );

        Instant now = Instant.now();
        RLock lock = redissonClient.getLock("subscription:lock:" + subscriptionNo);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);
            if (idempotencyLock != null) {
                log.info(
                        "handleSubscriptionCheckoutFailedEvent: subscription already processed after lock, eventId={}, finish request",
                        eventId
                );
                return;
            }

            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn(
                        "handleSubscriptionCheckoutFailedEvent: Failed to acquire lock for subscription {}, finish request",
                        subscriptionNo
                );
                throw new BizException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            int isUpdated = customerSubscriptionService.updateFromProviderIfStatusChanged(
                    subscriptionNo,
                    SubscriptionStatus.CREATED,
                    SubscriptionStatus.INITIAL_FAIL,
                    null,
                    null,
                    null,
                    now
            );

            if (isUpdated == 1) {
                log.info("handleSubscriptionCheckoutFailedEvent: initial subscription checkout failed, subscriptionNo={}", subscriptionNo);
                SubscriptionHistoryService.AppendResult appendResult =
                        subscriptionHistoryService.appendInitialFailEventIfLatestPaying(subscriptionNo);

                if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.HISTORY_MISSING) {
                    Optional<SubscriptionHistory> bySessionId =
                            subscriptionHistoryService.findBySubscription_subscriptionNo(subscriptionNo);
                    if (bySessionId.isEmpty()) {
                        abnormalOrchestrator.upsertAbnormalOrder(
                                subscriptionNo,
                                eventId,
                                BusinessEventType.SUBSCRIPTION_HISTORY_MISSING,
                                session.getId(),
                                "handleSubscriptionCheckout_fail_subscriptionHistory",
                                "subscriptionHistory_change_to_INITIAL_FAIL",
                                AbnormalOrderType.SUBSCRIPTION_MISSING,
                                ctx.getProvider(),
                                null
                        );
                        ctx.setAbnormalAlreadyUpserted(true);
                        redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                        throw new BizException(ErrorCode.SUBSCRIPTION_HISTORY_NOT_FOUND);
                    }
                }
                if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.LATEST_PAYMENT_STATUS_MISMATCH) {
                    log.warn("append initial-fail history skipped due to latest payment status mismatch, subscriptionNo={}", subscriptionNo);
                }

                backfillSubscriptionProviderIdentity(subscriptionNo, session);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }

            Optional<CustomerSubscription> bySubscriptionNo =
                    customerSubscriptionService.findBySubscriptionNo(subscriptionNo);
            if (bySubscriptionNo.isEmpty()) {
                abnormalOrchestrator.upsertAbnormalOrder(
                        subscriptionNo,
                        eventId,
                        BusinessEventType.CUSTOMER_SUBSCRIPTION_MISSING,
                        session.getId(),
                        "handleSubscription_CUSTOMER_SUBSCRIPTION_MISSING",
                        "CUSTOMER_SUBSCRIPTION_MISSING",
                        AbnormalOrderType.SUBSCRIPTION_MISSING,
                        ctx.getProvider(),
                        null
                );
                ctx.setAbnormalAlreadyUpserted(true);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                throw new BizException(ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND);
            }

            backfillSubscriptionProviderIdentity(subscriptionNo, session);
            SubscriptionHistoryService.AppendResult appendResult =
                    subscriptionHistoryService.appendInitialFailEventIfLatestPaying(subscriptionNo);
            if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.LATEST_PAYMENT_STATUS_MISMATCH) {
                log.warn("idempotent path append initial-fail skipped due to latest payment status mismatch, subscriptionNo={}", subscriptionNo);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(
                    "handleSubscriptionCheckoutFailedEvent:Interrupted while acquiring lock, waiting for reconcile, subscriptionNo={}",
                    subscriptionNo
            );
            throw new BizException(ErrorCode.LOCK_INTERRUPTED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Order branch extracted from checkout.session.completed (mode=payment).
     */
    public void handleOrderPaymentCompleted(WebhookContext ctx) throws Exception {
        CheckoutSessionWebhookEvent event =
                webhookObjectParserRouter.parseAs(
                        ctx.getProviderRawEvent(),
                        ctx.getEventType(),
                        CheckoutSessionWebhookEvent.class
                );
        ctx.setProviderEvent(event);
        CheckoutSessionWebhookEvent.CheckoutSessionObject session = event.getObject();

        if (session == null) {
            throw new BizException(ErrorCode.STRIPE_SESSION_NOT_FOUND);
        }

        String sessionId = session.getId();
        String paymentStatus = session.getPaymentStatus();
        if (!"paid".equals(paymentStatus)) {
            log.warn("payment not completed, status={}", paymentStatus);
            throw new BizException(ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE);
        }

        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || !metadata.containsKey("orderNo")) {
            log.error("orderNo missing in metadata");
            throw new BizException(ErrorCode.JSON_ERROR);
        }

        String orderNo = metadata.get("orderNo");
        ctx.setTrackingId(orderNo);
        ctx.setProviderTrackingId(sessionId);
        String paymentIntent = session.getPaymentIntent();
        Long amount = session.getAmountTotal();

        log.info(
                "Payment success: orderNo={}, amount={}, pi={}, sessionId={}",
                orderNo,
                amount,
                paymentIntent,
                sessionId
        );

        // Final transition is protected by event/session idempotency gates.
        handlePaymentSessionSuccess(ctx);
    }

    /**
     * Order branch extracted from checkout.session.async_payment_failed (mode=payment).
     */
    public void handleOrderPaymentFailed(WebhookContext ctx) throws Exception {
        CheckoutSessionWebhookEvent event =
                webhookObjectParserRouter.parseAs(
                        ctx.getProviderRawEvent(),
                        ctx.getEventType(),
                        CheckoutSessionWebhookEvent.class
                );

        CheckoutSessionWebhookEvent.CheckoutSessionObject session = event.getObject();
        String eventId = ctx.getEventId();

        if (session == null) {
            throw new BizException(ErrorCode.STRIPE_SESSION_NOT_FOUND);
        }

        String sessionId = session.getId();
        Map<String, String> metadata = session.getMetadata();
        String orderNo = metadata == null ? null : metadata.get("orderNo");
        if (orderNo != null && !orderNo.isBlank()) {
            ctx.setTrackingId(orderNo);
        }
        ctx.setProviderTrackingId(sessionId);

        Optional<Order> orderBySessionId = orderRepository.findBySessionId(sessionId);
        if (orderBySessionId.isEmpty()) {
            abnormalOrchestrator.upsertAbnormalOrder(
                    orderNo,
                    eventId,
                    BusinessEventType.ORDER_PAYMENT_FAILED,
                    session.getId(),
                    "handlePaymentFail_order_missing",
                    "order_missing",
                    AbnormalOrderType.ORDER_MISSING,
                    event.getProvider(),
                    null
            );
            ctx.setAbnormalAlreadyUpserted(true);
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        Payment payment = paymentService.findBySessionId(sessionId);
        if (payment == null) {
            abnormalOrchestrator.upsertAbnormalOrder(
                    orderNo,
                    eventId,
                    BusinessEventType.ORDER_PAYMENT_FAILED,
                    session.getId(),
                    "handlePaymentFail_payment_missing",
                    "payment_missing",
                    AbnormalOrderType.PAYMENT_MISSING,
                    event.getProvider(),
                    null
            );
            ctx.setAbnormalAlreadyUpserted(true);
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        Order order = orderBySessionId.get();

        // Ignore late failure callback after local success is finalized.
        if (order.getStatus() == OrderStatus.PAID
                && payment.getStatus() == PaymentStatus.SUCCESS) {
            return;
        }

        if (order.getStatus() == OrderStatus.PAYMENT_FAILED_RETRYABLE
                && payment.getStatus() == PaymentStatus.FAILED) {
            throw new BizException(ErrorCode.ORDER_FAILED);
        }

        order.setStatus(OrderStatus.PAYMENT_FAILED_RETRYABLE);
        order.setActiveKey(null);
        order.setUpdatedAt(LocalDateTime.now());
        paymentService.recordFailEnding(payment, PaymentStatus.FAILED, event.getEventType().toString());
        paymentRepository.save(payment);
    }

    /**
     * Final success-state transition for order/payment.
     */
    private void handlePaymentSessionSuccess(WebhookContext ctx) {
        CheckoutSessionWebhookEvent event = (CheckoutSessionWebhookEvent) ctx.getProviderEvent();
        String eventId = ctx.getEventId();
        String sessionId = ctx.getProviderTrackingId();
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }
        String idempotentKey = "PaymentSessionSuccess:event:" + eventId;

        RLock lock = redissonClient.getLock("session:lock:" + sessionId);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);

            if (idempotencyLock != null) {
                log.info("event already processed after lock, eventId={}, finish request", eventId);
                return;
            }

            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("Failed to acquire lock for session {}, finish request", sessionId);
                throw new BizException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            int updated = orderRepository.markPaidIfNotPaid(sessionId, LocalDateTime.now());
            if (updated == 1) {
                log.info("webhookSession success: {}", sessionId);
                int i = paymentService.markPaidIfNotPaid(sessionId);
                if (i == 0) {
                    Payment bySessionId = paymentService.findBySessionId(sessionId);
                    if (bySessionId == null) {
                        abnormalOrchestrator.upsertAbnormalOrder(
                                ctx.getTrackingId(),
                                eventId,
                                BusinessEventType.ORDER_PAYMENT_FAILED,
                                sessionId,
                                "handlePaymentFail_payment_missing",
                                "PAYMENT_MISSING",
                                AbnormalOrderType.PAYMENT_MISSING,
                                event.getProvider(),
                                null
                        );
                        ctx.setAbnormalAlreadyUpserted(true);
                        redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                        throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
                    }
                }
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }

            Optional<Order> bySessionId = orderRepository.findBySessionId(sessionId);
            if (bySessionId.isEmpty()) {
                abnormalOrchestrator.upsertAbnormalOrder(
                        ctx.getTrackingId(),
                        eventId,
                        BusinessEventType.ORDER_PAYMENT_FAILED,
                        sessionId,
                        "handlePaymentFail_order_missing",
                        "ORDER_MISSING",
                        AbnormalOrderType.ORDER_MISSING,
                        event.getProvider(),
                        null
                );
                ctx.setAbnormalAlreadyUpserted(true);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                throw new BizException(ErrorCode.ORDER_NOT_FOUND);
            }

            redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
            log.info("Order already paid, sessionId={}, finish request", sessionId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while acquiring lock, waiting for reconcile, sessionId={}", sessionId);
            throw new BizException(ErrorCode.LOCK_INTERRUPTED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void backfillSubscriptionProviderIdentity(
            String subscriptionNo,
            CheckoutSessionWebhookEvent.CheckoutSessionObject session
    ) {
        if (subscriptionNo == null || subscriptionNo.isBlank() || session == null) {
            return;
        }

        Optional<CustomerSubscription> localOptional = customerSubscriptionService.findBySubscriptionNo(subscriptionNo);
        if (localOptional.isEmpty()) {
            return;
        }

        CustomerSubscription local = localOptional.get();
        boolean changed = false;

        String providerSubscriptionId = session.getSubscription();
        if (providerSubscriptionId != null
                && !providerSubscriptionId.isBlank()
                && !providerSubscriptionId.equals(local.getProviderSubscriptionId())) {
            local.setProviderSubscriptionId(providerSubscriptionId);
            changed = true;
        }

        String providerCustomerId = session.getCustomer();
        if (providerCustomerId != null
                && !providerCustomerId.isBlank()
                && !providerCustomerId.equals(local.getProviderCustomerId())) {
            local.setProviderCustomerId(providerCustomerId);
            changed = true;
        }

        if (changed) {
            local.setUpdatedAt(LocalDateTime.now());
            customerSubscriptionService.save(local);
        }
    }
}
