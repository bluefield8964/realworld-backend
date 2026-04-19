package realworld_backend.service.CommerceService;

import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
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
import realworld_backend.repository.CommerceRepository.PaymentRepository;
import realworld_backend.repository.OrderRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {
    // Stripe event retry limit in local processing pipeline.
    private final int MAX_ATTEMPTS = 5;
    // A PROCESSING record older than this is considered stale and can be taken over.
    private final Duration PROCESSING_STALE = Duration.ofSeconds(10);

    private final AbnormalOrderService abnormalOrderService;
    private final StripeEventService stripeEventService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;

    /**
     * Webhook entry point:
     * 1) verify signature and parse event
     * 2) filter by event type
     * 3) route actionable types into idempotent processing
     */
    @Transactional
    public void handleStripeEvent(String payload, String sigHeader, String secret) throws Exception {
        Event event = Webhook.constructEvent(payload, sigHeader, secret);
        String type = event.getType();
        String eventId = event.getId();

        log.info("EVENT: {}", type);

        switch (type) {
            case "checkout.session.completed", "checkout.session.async_payment_failed":
                handleStripeEventProcess(type, event);
                break;
            case "payment_intent.created":
                log.info("payment_intent.created: {}", eventId);
                break;
            case "payment_intent.succeeded":
                log.info("payment_intent.succeeded: {}", eventId);
                break;
            default:
                // Ignore unrelated event types.
                break;
        }
    }

    /**
     * Idempotent processing for session-level events:
     * - reserve or reuse StripeEvent row
     * - apply retry/dead/concurrency policy
     * - execute business logic
     * - persist final event status
     */
    public void handleStripeEventProcess(String type, Event event) throws Exception, BizException {
        String json = event.getData().getObject().toJson();
        Session session = Session.GSON.fromJson(json, Session.class);
        if (session == null) {
            throw new BizException(ErrorCode.TRIPE_SESSION_NOT_FOUND);
        }

        String eventId = event.getId();
        StripeEvent ev;

        // 1) Event reservation / idempotency gate.
        try {
            stripeEventService.saveProcessing(eventId, type);
        } catch (DataIntegrityViolationException e) {
            try {
                ev = stripeEventService.findByIdForUpdateOrThrow(eventId);
            } catch (Exception notFoundAfterConflict) {
                //this exc can maybe remove at next webhook , so dont need to attach to abnormalOrder immediately
                /* Duplicate key happened, but row is missing: record and ask Stripe to retry.
                abnormalOrderService.upsertAbnormalOrder(
                        null,
                        eventId,
                        type,
                        session.getId(),
                        "event_row_missing_after_conflict",
                        "conflict happened but stripe_event row not found; ask Stripe retry",
                        AbnormalOrderStatus.PROCESSING
                );*/
                throw new IllegalStateException(
                        "stripe_event missing after duplicate conflict: " + eventId,
                        notFoundAfterConflict
                );
            }

            if (ev.getStatus() == StripeEventStatus.SUCCEEDED) {
                log.info("event already processed, eventId: {}, type: {}", eventId, event.getType());
                return;
            }

            if (ev.getStatus() == StripeEventStatus.DEAD) {
                log.info("event already dead, need alarm or reconcile, eventId: {}, type: {}", eventId, event.getType());
                abnormalOrderService.upsertAbnormalOrder(
                        null,
                        eventId,
                        type,
                        session.getId(),
                        "handleStripeEvent_try_dead",
                        "event already dead",
                        AbnormalOrderStatus.RETRY_EXHAUSTED
                );
                return;
            }

            if (ev.getAttempts() >= MAX_ATTEMPTS) {
                stripeEventService.markFailStatusNoAttempt(
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
                        AbnormalOrderStatus.RETRY_EXHAUSTED
                );
                log.error(
                        "event already processed, eventId: {}, type: {}, but failed 5 times, will not retry",
                        eventId,
                        event.getType()
                );
                return;
            }
            //event still processing , stripe multi-sending webhook
            if (ev.getStatus() == StripeEventStatus.PROCESSING
                    && ev.getEventHandledAt() != null
                    && ev.getEventHandledAt().isAfter(LocalDateTime.now().minus(PROCESSING_STALE.multipliedBy(ev.getAttempts())))) {
                log.error("event still processing by another worker: {}", eventId);
                return;
            }
            //because ev.getEventHandledAt().isAfter(LocalDateTime.now().minus(PROCESSING_STALE)
            //last event already processing 2 mins , must be something wrong , need to retry
            // FAILED or stale PROCESSING: take ownership and continue.
            stripeEventService.markFailStatusNoAttempt(
                    eventId,
                    type,
                    StripeEventStatus.PROCESSING,
                    null,
                    ev.getAttempts(),
                    1
            );
        }

        // 2) Execute business processing.
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
        } catch (Exception ex) {
            // 3) Record failure state, then rethrow to let Stripe retry.
            StripeEvent current = stripeEventService.findByIdForUpdateOrThrow(eventId);
            if (current.getAttempts() >= MAX_ATTEMPTS) {
                stripeEventService.markFailStatusNoAttempt(
                        eventId,
                        type,
                        StripeEventStatus.DEAD,
                        "max attempts exceeded",
                        current.getAttempts(),
                        1
                );
                abnormalOrderService.upsertAbnormalOrder(
                        null,
                        eventId,
                        type,
                        session.getId(),
                        "handleStripeEvent_max_attempts",
                        "max attempts exceeded",
                        AbnormalOrderStatus.RETRY_EXHAUSTED
                );
                log.error("event moved to DEAD after failures. eventId={}", eventId);
                return;
            } else {
                stripeEventService.markFailStatusNoAttempt(
                        eventId,
                        type,
                        StripeEventStatus.FAILED,
                        "handleStripeEvent" + ex.getMessage(),
                        current.getAttempts(),
                        1
                );
            }

            throw ex;
        }
    }

    /**
     * Handle checkout.session.async_payment_failed event.
     */
    private void handlePaymentFail(Event event) throws Exception, BizException {
        String json = event.getData().getObject().toJson();
        String eventId = event.getId();
        Session session = Session.GSON.fromJson(json, Session.class);

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
                    AbnormalOrderStatus.ORDER_MISSING
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
                    AbnormalOrderStatus.PAYMENT_MISSING
            );
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        Order order = orderBySessionId.get();
        Payment payment = paymentBySessionId.get();

        // Ignore inconsistent failed callback if local state is already paid/success.
        if (orderBySessionId.get().getStatus() == OrderStatus.PAID
                && paymentBySessionId.get().getStatus() == PaymentStatus.SUCCESS) {
            return;
        } else {
            if (orderBySessionId.get().getStatus() == OrderStatus.PAYMENT_FAILED_RETRYABLE
                    && paymentBySessionId.get().getStatus() == PaymentStatus.FAILED) {
                return;
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
     * Handle checkout.session.completed event.
     */
    private void handleCheckoutSessionCompleted(Event event) throws Exception, BizException {
        String json = event.getData().getObject().toJson();
        Session session = Session.GSON.fromJson(json, Session.class);
        log.info("Deserialized session via GSON, payload={}", json);

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

        // Core success transition based on eventId + sessionId.
        handlePaymentSessionSuccess(event.getId(), sessionId);
    }

    /**
     * Critical module for successful payment transition.
     */
    public void handlePaymentSessionSuccess(String eventId, String sessionId) {
        String idempotentKey = "stripe:event:" + eventId;

        RLock lock = redissonClient.getLock("session:lock:" + sessionId);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);

            if (idempotencyLock != null) {
                log.info("Stripe event already processed after lock, eventId={}, finish request", eventId);
                return;
            }

            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("Failed to acquire lock for session {}, finish request", sessionId);
                //throw this even will trigger stripe send one more webhook , but it can accelerate the order process
                throw new IllegalStateException("lock not acquired for sessionId=" + sessionId);
            }

            int updated = orderRepository.markPaidIfNotPaid(sessionId, LocalDateTime.now());

            if (updated == 1) {
                log.info("webhookSession success: {}", sessionId);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                paymentRepository.markPaidIfNotPaid(sessionId,LocalDateTime.now());
            } else {
                Optional<Order> byStripeSessionId = orderRepository.findByStripeSessionId(sessionId);
                if (!byStripeSessionId.isPresent()) {
                    abnormalOrderService.upsertAbnormalOrder(
                            null,
                            eventId,
                            "checkout.session.completed",
                            sessionId,
                            "handlePaymentFail_order_missing",
                            "ORDER_MISSING",
                            AbnormalOrderStatus.ORDER_MISSING
                    );
                    //it will trigger stripe send one more time webhook, maybe order exist, it only db problem,
                    throw new BizException(ErrorCode.ORDER_NOT_FOUND);
                }
                log.info("Order already paid, sessionId={}, finish request", sessionId);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while acquiring lock, waiting for reconcile, sessionId={}", sessionId);
            throw new IllegalStateException("interrupted while acquiring lock", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
