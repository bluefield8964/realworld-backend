package realworld_backend.service.CommerceService.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.dto.Exception.BizException;
import realworld_backend.dto.Exception.ErrorCode;
import realworld_backend.model.commerceModule.*;
import realworld_backend.model.commerceModule.core.ProviderEvent;
import realworld_backend.repository.CommerceRepository.OrderRepository;
import realworld_backend.repository.CommerceRepository.PaymentRepository;
import realworld_backend.service.CommerceService.core.*;
import realworld_backend.service.CommerceService.impl.RetryPolicyImpl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Core Stripe webhook processor.
 * Handles type filter, idempotency, retry policy, and state transitions.
 */
public class WebhookService {
    private final AbnormalOrderService abnormalOrderService;
    private final StripeEventService stripeEventService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final RetryPolicyImpl retryPolicy;
    private final WebhookErrorPolicy webhookErrorPolicy;
    private final PaymentChannelRouter paymentChannelRouter;

    /**
     * Webhook entry: verify signature, filter type, then route actionable events.
     */
    public void handleStripeEvent(String payload, String sigHeader, String secret, String provider) throws WebhookDecisionException {
        try {
            PaymentChannel paymentChannel = paymentChannelRouter.get(provider);
            ProviderEvent providerEvent;
            providerEvent = paymentChannel.parseWebhook(payload, sigHeader, secret);

            String type = providerEvent.getType();
            String eventId = providerEvent.getId();
            log.info("EVENT: {}", type);
            switch (type) {
                case "checkout.session.completed", "checkout.session.async_payment_failed":
                    handleStripeEventProcess(providerEvent);
                    break;
                case "payment_intent.created":
                    log.info("payment_intent.created: {}", eventId);
                    break;
                case "payment_intent.succeeded":
                    log.info("payment_intent.succeeded: {}", eventId);
                    break;
                default:
                    // Non-actionable events are intentionally ignored.
                    break;
            }

        } catch (WebhookDecisionException e) {
            throw e;
        } catch (PaymentChannelException e) {
            WebhookDecision decision = webhookErrorPolicy.classify(e);
            if (decision.upsertAbnormal()) {
                abnormalOrderService.upsertAbnormalOrder(
                        null,
                        null,
                        null,
                        null,
                        "payment_channel_exception",
                        e.getMessage(),
                        decision.abnormalOrderStatus(),
                        e.getProvider(),
                        e.getRequestId()
                );
            }
            throw new WebhookDecisionException(decision, e);
        } catch (BizException e) {
            WebhookDecision decision = webhookErrorPolicy.classify(e);
            throw new WebhookDecisionException(decision, e);
        } catch (Exception e) {
            WebhookDecision decision = webhookErrorPolicy.classify(e);
            throw new WebhookDecisionException(decision, e);
        }
    }

    /**
     * Event-level idempotent pipeline for session callbacks.
     */
    @Transactional
    public void handleStripeEventProcess(ProviderEvent event) throws Exception {
        ProviderEvent.ProviderSession session = event.getData().getObject();
        if (session == null) {
            throw new BizException(ErrorCode.TRIPE_SESSION_NOT_FOUND);
        }

        String eventId = event.getId();
        StripeEvent ev;
        String type = event.getType();
        // 1) Reserve event row (or load existing row on duplicate key).
        try {
            stripeEventService.saveProcessing(eventId, type);
        } catch (DataIntegrityViolationException e) {
            try {
                ev = stripeEventService.findByIdForUpdateOrThrow(eventId);


            } catch (Exception notFoundAfterConflict) {
                // Visibility race after duplicate key; retry later.
                throw new BizException(ErrorCode.EVENT_PROCESSING);
            }

            if (ev.getStatus() == StripeEventStatus.SUCCEEDED) {
                log.info("event already processed, eventId: {}, type: {}", eventId, type);
                return;
            }

            if (ev.getStatus() == StripeEventStatus.DEAD) {
                log.info("event already dead, need alarm or reconcile, eventId: {}, type: {}", eventId, type);
                // Already terminal locally; keep abnormal trace current.
                abnormalOrderService.upsertAbnormalOrder(
                        null,
                        eventId,
                        type,
                        session.getId(),
                        "handleStripeEvent_try_dead",
                        "event already dead",
                        AbnormalOrderStatus.RETRY_EXHAUSTED,
                        event.getProvider(),
                        null
                );
                throw new BizException(ErrorCode.RETRY_EXHAUSTED);
            }

            if (retryPolicy.exhausted(ev.getAttempts())) {
                // Exhausted retries: mark DEAD and hand over to abnormal queue.
                stripeEventService.markStripeEventStatusWithAttempt(
                        eventId,
                        type,
                        StripeEventStatus.DEAD,
                        "max attempts exceeded",
                        ev.getAttempts(),
                        1
                );
                abnormalOrderService.upsertAbnormalOrder(
                        null,
                        eventId,
                        type,
                        session.getId(),
                        "handleStripeEvent_max_attempts",
                        "max attempts exceeded",
                        AbnormalOrderStatus.RETRY_EXHAUSTED,
                        event.getProvider(),
                        null
                );
                log.error(
                        "event already processed, eventId: {}, type: {}, but failed 5 times, will not retry",
                        eventId,
                        event.getType()
                );
                throw new BizException(ErrorCode.RETRY_EXHAUSTED);
            }

            // Fresh PROCESSING lease means another worker still owns this event.
            if (ev.getStatus() == StripeEventStatus.PROCESSING
                    && ev.getEventHandledAt() != null
                    && LocalDateTime.now().isBefore(retryPolicy.mainStreamNextRetryAt(ev.getAttempts(), ev.getEventHandledAt()))) {
                log.warn("event still processing by another worker: {}", eventId);

                throw new BizException(ErrorCode.EVENT_PROCESSING);
            }
            // FAILED or stale PROCESSING can be safely re-taken.
            stripeEventService.markStripeEventStatusWithAttempt(
                    eventId,
                    type,
                    StripeEventStatus.PROCESSING,
                    null,
                    ev.getAttempts(),
                    0
            );
        }

        // 2) Execute type-specific business handler.
        try {
            switch (type) {
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted(event);
                    stripeEventService.markSuccessStatusAndIncAttempt(eventId, StripeEventStatus.SUCCEEDED, null);
                    break;
                case "checkout.session.async_payment_failed":
                    handlePaymentFail(event);
                    stripeEventService.markSuccessStatusAndIncAttempt(eventId, StripeEventStatus.SUCCEEDED, null);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            StripeEvent current = stripeEventService.findByIdForUpdateOrThrow(eventId);
            WebhookDecision decision = webhookErrorPolicy.classify(e);
            stripeEventService.markStripeEventStatusWithAttempt(
                    eventId,
                    type,
                    decision.stripeEventStatus(),
                    e.getMessage(),
                    current.getAttempts(),
                    1
            );
            if (decision.upsertAbnormal()) {
                abnormalOrderService.upsertAbnormalOrder(
                        null,
                        eventId,
                        type,
                        session.getId(),
                        "webhook_policy",
                        e.getMessage(),
                        decision.abnormalOrderStatus(),
                        event.getProvider(),
                        null
                );
            }
            throw new WebhookDecisionException(decision, e);
        }
    }


    /**
     * Handles async payment-failed callback.
     * Keeps order/payment in retryable failed states unless already finalized as paid.
     */
    private void handlePaymentFail(ProviderEvent event) throws Exception, BizException {
        ProviderEvent.ProviderSession session = event.getData().getObject();
        String eventId = event.getId();

        if (session == null) {
            throw new BizException(ErrorCode.TRIPE_SESSION_NOT_FOUND);
        }

        String sessionId = session.getId();

        Optional<Order> orderBySessionId = orderRepository.findByStripeSessionId(sessionId);
        if (orderBySessionId.isEmpty()) {
            abnormalOrderService.upsertAbnormalOrder(
                    null,
                    eventId,
                    "checkout.session.async_payment_failed",
                    session.getId(),
                    "handlePaymentFail_order_missing",
                    "order_missing",
                    AbnormalOrderStatus.ORDER_MISSING,
                    event.getProvider(),
                    null
            );
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        Optional<Payment> paymentBySessionId = paymentRepository.findBySessionId(sessionId);
        if (paymentBySessionId.isEmpty()) {
            abnormalOrderService.upsertAbnormalOrder(
                    null,
                    eventId,
                    "checkout.session.async_payment_failed",
                    session.getId(),
                    "handlePaymentFail_payment_missing",
                    "payment_missing",
                    AbnormalOrderStatus.PAYMENT_MISSING,
                    event.getProvider(),
                    null
            );
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        Order order = orderBySessionId.get();
        Payment payment = paymentBySessionId.get();

        // Ignore late failure callback after local success is finalized.
        if (orderBySessionId.get().getStatus() == OrderStatus.PAID
                && paymentBySessionId.get().getStatus() == PaymentStatus.SUCCESS) {
            return;
        } else {
            if (orderBySessionId.get().getStatus() == OrderStatus.PAYMENT_FAILED_RETRYABLE
                    && paymentBySessionId.get().getStatus() == PaymentStatus.FAILED) {
                throw new BizException(ErrorCode.ORDER_FAILED);
            }

            order.setStatus(OrderStatus.PAYMENT_FAILED_RETRYABLE);
            order.setUpdatedAt(LocalDateTime.now());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorMsg(event.getType());
            orderRepository.save(order);
            paymentRepository.save(payment);
        }
    }

    /**
     * Handles successful checkout callback.
     * Validates payload then delegates final state transition.
     */
    private void handleCheckoutSessionCompleted(ProviderEvent event) throws Exception, BizException {
        ProviderEvent.ProviderSession session = event.getData().getObject();

        if (session == null) {
            throw new BizException(ErrorCode.TRIPE_SESSION_NOT_FOUND);
        }

        String sessionId = session.getId();
        String paymentStatus = session.getPaymentStatus();

        if (!"paid".equals(paymentStatus)) {
            log.warn("payment not completed, status={}", paymentStatus);
            throw new BizException(ErrorCode.JSON_ERROR);
        }

        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || !metadata.containsKey("orderNo")) {
            log.error("orderNo missing in metadata");
            throw new BizException(ErrorCode.JSON_ERROR);
        }

        String orderNo = metadata.get("orderNo");
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
        handlePaymentSessionSuccess(event, sessionId);
    }

    /**
     * Final success-state transition for order/payment.
     * Uses Redis event key + session lock + conditional DB updates.
     */
    public void handlePaymentSessionSuccess(ProviderEvent event, String sessionId) {
        String eventId = event.getId();
        String idempotentKey = "stripe:event:" + eventId;

        RLock lock = redissonClient.getLock("session:lock:" + sessionId);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);

            if (idempotencyLock != null) {
                // Event-level idempotency hit.
                log.info("Stripe event already processed after lock, eventId={}, finish request", eventId);
                return;
            }

            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("Failed to acquire lock for session {}, finish request", sessionId);
                // Lock contention is treated as retryable.
                throw new BizException(ErrorCode.LOCK_CANT_REQUIRE);
            }

            int updated = orderRepository.markPaidIfNotPaid(sessionId, LocalDateTime.now());

            if (updated == 1) {
                log.info("webhookSession success: {}", sessionId);
                int i = paymentRepository.markPaidIfNotPaid(sessionId);

                if (i == 0) {
                    Optional<Payment> bySessionId = paymentRepository.findBySessionId(sessionId);
                    if (bySessionId.isEmpty()) {
                        abnormalOrderService.upsertAbnormalOrder(
                                null,
                                eventId,
                                "checkout.session.completed",
                                sessionId,
                                "handlePaymentFail_payment_missing",
                                "PAYMENT_MISSING",
                                AbnormalOrderStatus.PAYMENT_MISSING,
                                event.getProvider(),
                                null
                        );
                        // Set idempotent key to avoid webhook storm while abnormal flow takes over.
                        redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                        throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
                    }
                }
                // update=0 with existing payment is treated as idempotent success.
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }
            Optional<Order> byStripeSessionId = orderRepository.findByStripeSessionId(sessionId);
            if (byStripeSessionId.isEmpty()) {
                abnormalOrderService.upsertAbnormalOrder(
                        null,
                        eventId,
                        "checkout.session.completed",
                        sessionId,
                        "handlePaymentFail_order_missing",
                        "ORDER_MISSING",
                        AbnormalOrderStatus.ORDER_MISSING,
                        event.getProvider(),
                        null
                );
                // Missing order is handed over to abnormal reconciliation.
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                throw new BizException(ErrorCode.ORDER_NOT_FOUND);
            }
            // Already PAID is a normal idempotent result.
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
}