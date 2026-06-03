package realworld_backend.commerce.service.statemachine;

import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.OrderStatus;
import realworld_backend.commerce.model.PaymentStatus;

/**
 * State-machine decision for order/payment webhook transitions.
 */
public record OrderWebhookStateDecision(
        BusinessEventType eventType,
        OrderStatus currentOrderStatus,
        PaymentStatus currentPaymentStatus,
        OrderStatus nextOrderStatus,
        PaymentStatus nextPaymentStatus,
        StateNature currentOrderStateNature,
        StateNature nextOrderStateNature,
        StateNature currentPaymentStateNature,
        StateNature nextPaymentStateNature,
        TransitionClass transitionClass,
        boolean shouldPersist,
        boolean shouldRaiseAbnormal,
        String reason
) {
}
