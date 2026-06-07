package realworld_backend.commerce.service.webhook;

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
import realworld_backend.commerce.model.Order;
import realworld_backend.commerce.model.Payment;
import realworld_backend.commerce.model.checkoutPayment.CheckoutSessionWebhookEvent;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.service.AbnormalOrchestrator;
import realworld_backend.commerce.service.PaymentService;
import realworld_backend.commerce.service.order.OrderService;
import realworld_backend.commerce.service.statemachine.OrderWebhookStateDecision;
import realworld_backend.commerce.service.statemachine.OrderWebhookStateMachine;
import realworld_backend.commerce.service.statemachine.StateNature;
import realworld_backend.commerce.service.statemachine.TransitionClass;
import realworld_backend.commerce.service.subscription.checkout.SubscriptionCheckoutSessionWebhookService;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.webhook.parser.WebhookObjectParserRouter;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutSessionWebhookService {
    private final PaymentFailureEscalationService paymentFailureEscalationService;
    private final AbnormalOrchestrator abnormalOrchestrator;
    private final OrderService orderService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final WebhookObjectParserRouter webhookObjectParserRouter;
    private final PaymentService paymentService;
    private final OrderWebhookStateMachine orderWebhookStateMachine;
    private final SubscriptionCheckoutSessionWebhookService subscriptionCheckoutSessionWebhookService;

    @Transactional
    public void handleCheckoutSessionCompleted(WebhookContext ctx) throws Exception {
        CheckoutSessionWebhookEvent event =
                webhookObjectParserRouter.parseAs(
                        ctx.getProviderRawEvent(),
                        ctx.getEventType(),
                        CheckoutSessionWebhookEvent.class
                );
        CheckoutSessionWebhookEvent.CheckoutSessionObject session = event.getObject();
        ctx.setProviderEvent(event);
        if (session == null) {
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }

        String mode = session.getMode();
        log.info(
                "handleCheckoutSessionCompleted: received checkout.session.completed, eventId={}, sessionId={}, mode={}",
                ctx.getEventId(),
                session.getId(),
                mode
        );
        if ("payment".equals(mode)) {
            handleOrderPaymentCompleted(ctx, session);
            return;
        }

        if ("subscription".equals(mode)) {
            handleSubscriptionCheckoutCompletedEvent(ctx, session);
            return;
        }

        throw new BizException(ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE);
    }

    @Transactional
    public void handleCheckoutSessionAsyncPaymentFailed(WebhookContext ctx) throws Exception {
        CheckoutSessionWebhookEvent event =
                webhookObjectParserRouter.parseAs(
                        ctx.getProviderRawEvent(),
                        ctx.getEventType(),
                        CheckoutSessionWebhookEvent.class
                );
        CheckoutSessionWebhookEvent.CheckoutSessionObject session = event.getObject();
        ctx.setProviderEvent(event);
        if (session == null) {
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }

        String mode = session.getMode();
        log.info(
                "handleCheckoutSessionAsyncPaymentFailed: received checkout.session.async_payment_failed, eventId={}, sessionId={}, mode={}",
                ctx.getEventId(),
                session.getId(),
                mode
        );
        if ("payment".equals(mode)) {
            handleOrderPaymentFailed(ctx, session);
            return;
        }

        if ("subscription".equals(mode)) {
            handleSubscriptionCheckoutFailedEvent(ctx, session);
            return;
        }

        throw new BizException(ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE);
    }

    /**
     * Best-effort placeholder for expired checkout sessions.
     * Intentionally light-weight: resolves business ids and logs, but leaves final business reaction open.
     */
    @Transactional
    public void handleCheckoutSessionExpired(WebhookContext ctx) {
        CheckoutSessionWebhookEvent event =
                webhookObjectParserRouter.parseAs(
                        ctx.getProviderRawEvent(),
                        ctx.getEventType(),
                        CheckoutSessionWebhookEvent.class
                );
        CheckoutSessionWebhookEvent.CheckoutSessionObject session = event == null ? null : event.getObject();
        ctx.setProviderEvent(event);
        if (session == null) {
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }
        String mode = session.getMode();
        log.info(
                "handleCheckoutSessionExpired: received checkout.session.expired, eventId={}, sessionId={}, mode={}",
                ctx.getEventId(),
                session.getId(),
                mode
        );
        if ("payment".equals(mode)) {
            handleOrderCheckoutSessionExpired(ctx, session);
            return;
        }

        if ("subscription".equals(mode)) {
            handleSubscriptionSessionExpired(ctx, session);
            return;
        }

        throw new BizException(ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE);
    }

    private void handleSubscriptionSessionExpired(WebhookContext ctx, CheckoutSessionWebhookEvent.CheckoutSessionObject session) {
        subscriptionCheckoutSessionWebhookService.handleSubscriptionSessionExpired(ctx, session);
    }

    private void handleOrderCheckoutSessionExpired(WebhookContext ctx, CheckoutSessionWebhookEvent.CheckoutSessionObject session) {
        if (session == null) {
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }

        String sessionId = session.getId();
        Map<String, String> metadata = session.getMetadata();
        String orderNo = metadata == null ? null : metadata.get("orderNo");
        if (orderNo != null && !orderNo.isBlank()) {
            ctx.setTrackingId(orderNo);
        }
        ctx.setProviderTrackingId(sessionId);
        executeOrderTransition(
                ctx,
                BusinessEventType.CHECKOUT_SESSION_EXPIRED,
                "CheckoutSessionExpired:event:" + ctx.getEventId(),
                BusinessEventType.CHECKOUT_SESSION_EXPIRED,
                "handleCheckoutExpired_order_missing",
                "handleCheckoutExpired_payment_missing"
        );
    }

    private void handleSubscriptionCheckoutCompletedEvent(WebhookContext ctx, CheckoutSessionWebhookEvent.CheckoutSessionObject session) {
        subscriptionCheckoutSessionWebhookService.handleSubscriptionCheckoutCompletedEvent(ctx, session);
    }


    private void handleSubscriptionCheckoutFailedEvent(
            WebhookContext ctx,
            CheckoutSessionWebhookEvent.CheckoutSessionObject session
    ) {
        subscriptionCheckoutSessionWebhookService.handleSubscriptionCheckoutFailedEvent(ctx, session);
    }

    /**
     * Order branch extracted from checkout.session.completed (mode=payment).
     */
    public void handleOrderPaymentCompleted(WebhookContext ctx, CheckoutSessionWebhookEvent.CheckoutSessionObject session) throws Exception {


        if (session == null) {
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }

        String sessionId = session.getId();
        log.info(
                "handleOrderPaymentCompleted: validating payment checkout, eventId={}, sessionId={}, paymentStatus={}, hasMetadata={}",
                ctx.getEventId(),
                sessionId,
                session.getPaymentStatus(),
                session.getMetadata() != null
        );
        String paymentStatus = session.getPaymentStatus();
        if (!"paid".equals(paymentStatus)) {
            log.warn("payment not completed, status={}", paymentStatus);
            throw new BizException(ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE);
        }

        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || !metadata.containsKey("orderNo")) {
            log.error("orderNo missing in metadata");
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
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
    public void handleOrderPaymentFailed(WebhookContext ctx, CheckoutSessionWebhookEvent.CheckoutSessionObject session) throws Exception {
        if (session == null) {
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }

        String sessionId = session.getId();
        Map<String, String> metadata = session.getMetadata();
        String orderNo = metadata == null ? null : metadata.get("orderNo");
        if (orderNo != null && !orderNo.isBlank()) {
            ctx.setTrackingId(orderNo);
        }
        ctx.setProviderTrackingId(sessionId);
        executeOrderTransition(
                ctx,
                BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED,
                "OrderPaymentFailed:event:" + ctx.getEventId(),
                BusinessEventType.ORDER_PAYMENT_FAILED,
                "handlePaymentFail_order_missing",
                "handlePaymentFail_payment_missing"
        );
    }

    /**
     * Final success-state transition for order/payment.
     */
    private void handlePaymentSessionSuccess(WebhookContext ctx) {
        String sessionId = ctx.getProviderTrackingId();
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }
        executeOrderTransition(
                ctx,
                BusinessEventType.CHECKOUT_SESSION_COMPLETED,
                "PaymentSessionSuccess:event:" + ctx.getEventId(),
                BusinessEventType.ORDER_PAYMENT_COMPLETED,
                "handlePaymentCompleted_order_missing",
                "handlePaymentCompleted_payment_missing"
        );
    }

    private void executeOrderTransition(
            WebhookContext ctx,
            BusinessEventType transitionEventType,
            String idempotentKey,
            BusinessEventType missingEventType,
            String orderMissingSource,
            String paymentMissingSource
    ) {
        CheckoutSessionWebhookEvent event = (CheckoutSessionWebhookEvent) ctx.getProviderEvent();
        String eventId = ctx.getEventId();
        String sessionId = ctx.getProviderTrackingId();
        if (event == null || sessionId == null || sessionId.isBlank()) {
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }

        RLock lock = redissonClient.getLock("session:lock:" + sessionId);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);
            if (idempotencyLock != null) {
                log.info("order transition already processed, eventId={}, transitionEventType={}", eventId, transitionEventType);
                throw new WebhookDuplicateIgnoredException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("failed to acquire order transition lock, sessionId={}, eventType={}", sessionId, transitionEventType);
                throw new WebhookDuplicateIgnoredException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            Order currentOrder = requireOrderForTransition(
                    ctx, event, eventId, sessionId, idempotentKey, missingEventType, orderMissingSource
            );
            Payment currentPayment = requirePaymentForTransition(
                    ctx, event, eventId, sessionId, idempotentKey, missingEventType, paymentMissingSource
            );

            OrderWebhookStateDecision decision = orderWebhookStateMachine.evaluate(
                    transitionEventType,
                    currentOrder.getStatus(),
                    currentPayment.getStatus()
            );

            if (decision.transitionClass() == TransitionClass.IGNORE_TRANSITION) {
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }
            if (!decision.shouldPersist()) {
                handleOrderIllegalDecision(ctx, decision);
            }

            if (tryApplyOrderTransition(currentOrder, currentPayment, decision)) {
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }

            currentOrder = requireOrderForTransition(
                    ctx, event, eventId, sessionId, idempotentKey, missingEventType, orderMissingSource
            );
            currentPayment = requirePaymentForTransition(
                    ctx, event, eventId, sessionId, idempotentKey, missingEventType, paymentMissingSource
            );
            decision = orderWebhookStateMachine.evaluate(
                    transitionEventType,
                    currentOrder.getStatus(),
                    currentPayment.getStatus()
            );
            if (decision.transitionClass() == TransitionClass.IGNORE_TRANSITION) {
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }
            if (decision.shouldPersist() && tryApplyOrderTransition(currentOrder, currentPayment, decision)) {
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }

            currentOrder = requireOrderForTransition(
                    ctx, event, eventId, sessionId, idempotentKey, missingEventType, orderMissingSource
            );
            currentPayment = requirePaymentForTransition(
                    ctx, event, eventId, sessionId, idempotentKey, missingEventType, paymentMissingSource
            );
            decision = orderWebhookStateMachine.evaluate(
                    transitionEventType,
                    currentOrder.getStatus(),
                    currentPayment.getStatus()
            );
            if (decision.transitionClass() == TransitionClass.IGNORE_TRANSITION) {
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }
            if (!decision.shouldPersist()) {
                handleOrderIllegalDecision(ctx, decision);
            }

            log.warn(
                    "order transition remained legal but CAS could not apply, eventType={}, eventId={}, trackingId={}, reason={}",
                    transitionEventType,
                    eventId,
                    ctx.getTrackingId(),
                    decision.reason()
            );
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("interrupted while acquiring order transition lock, sessionId={}, eventType={}", sessionId, transitionEventType);
            throw new BizException(ErrorCode.LOCK_INTERRUPTED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private boolean tryApplyOrderTransition(
            Order currentOrder,
            Payment currentPayment,
            OrderWebhookStateDecision decision
    ) {
        boolean clearActiveKey = decision.nextOrderStateNature() == StateNature.TERMINAL_STATE;
        int orderUpdated = orderService.markFromStatusToStatus(
                currentOrder.getId(),
                decision.currentOrderStatus(),
                decision.nextOrderStatus(),
                clearActiveKey,
                UtcTimeMapper.nowUtc()
        );
        int paymentUpdated = paymentService.markFromStatusToStatus(
                currentPayment.getId(),
                decision.currentPaymentStatus(),
                decision.nextPaymentStatus()
        );
        return orderUpdated == 1 && paymentUpdated == 1;
    }

    private Order requireOrderForTransition(
            WebhookContext ctx,
            CheckoutSessionWebhookEvent event,
            String eventId,
            String sessionId,
            String idempotentKey,
            BusinessEventType missingEventType,
            String missingSource
    ) {
        Order currentOrder = orderService.findBySessionId(sessionId);
        if (currentOrder != null) {
            return currentOrder;
        }
        abnormalOrchestrator.upsertAbnormalOrder(
                ctx.getTrackingId(),
                eventId,
                missingEventType,
                sessionId,
                missingSource,
                "ORDER_MISSING",
                AbnormalOrderType.ORDER_MISSING,
                event.getProvider(),
                null
        );
        ctx.setAbnormalAlreadyUpserted(true);
        redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
        throw new BizException(ErrorCode.ORDER_NOT_FOUND);
    }

    private Payment requirePaymentForTransition(
            WebhookContext ctx,
            CheckoutSessionWebhookEvent event,
            String eventId,
            String sessionId,
            String idempotentKey,
            BusinessEventType missingEventType,
            String missingSource
    ) {
        Payment currentPayment = paymentService.findBySessionId(sessionId);
        if (currentPayment != null) {
            return currentPayment;
        }
        abnormalOrchestrator.upsertAbnormalOrder(
                ctx.getTrackingId(),
                eventId,
                missingEventType,
                sessionId,
                missingSource,
                "PAYMENT_MISSING",
                AbnormalOrderType.PAYMENT_MISSING,
                event.getProvider(),
                null
        );
        ctx.setAbnormalAlreadyUpserted(true);
        redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
        throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
    }

    private void handleOrderIllegalDecision(
            WebhookContext ctx,
            OrderWebhookStateDecision orderWebhookStateDecision
    ) {
        if (orderWebhookStateDecision.transitionClass() == TransitionClass.RETRYABLE_ILLEGAL_TRANSITION) {
            log.warn(
                    "order transition is retryable-illegal, eventType={}, eventId={}, trackingId={}, reason={}",
                    orderWebhookStateDecision.eventType(),
                    ctx.getEventId(),
                    ctx.getTrackingId(),
                    orderWebhookStateDecision.reason()
            );
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        if (orderWebhookStateDecision.transitionClass() == TransitionClass.ILLEGAL_TRANSITION) {
            log.warn(
                    "order transition is illegal, eventType={}, eventId={}, trackingId={}, reason={}",
                    orderWebhookStateDecision.eventType(),
                    ctx.getEventId(),
                    ctx.getTrackingId(),
                    orderWebhookStateDecision.reason()
            );
            paymentFailureEscalationService.recordIncidentForException(
                    ctx,
                    null,
                    null,
                    orderWebhookStateDecision.reason(),
                    null,
                    null
            );
            throw new BizException(ErrorCode.PAYMENT_STATUS_CHANGE_FAIL);
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR);
    }

}
