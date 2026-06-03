package realworld_backend.commerce.service.statemachine;

import org.junit.jupiter.api.Test;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.OrderStatus;
import realworld_backend.commerce.model.PaymentStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderWebhookStateMachineTest {

    private final OrderWebhookStateMachine stateMachine = new OrderWebhookStateMachine();

    @Test
    void completedShouldTransitionPendingOrderToPaid() {
        OrderWebhookStateDecision decision = stateMachine.evaluate(
                BusinessEventType.CHECKOUT_SESSION_COMPLETED,
                OrderStatus.PENDING,
                PaymentStatus.PROCESSING
        );

        assertEquals(TransitionClass.LEGAL_TRANSITION, decision.transitionClass());
        assertEquals(OrderStatus.PAID, decision.nextOrderStatus());
        assertEquals(PaymentStatus.SUCCESS, decision.nextPaymentStatus());
        assertEquals(StateNature.INTERMEDIATE_STATE, decision.currentOrderStateNature());
        assertEquals(StateNature.TERMINAL_STATE, decision.nextOrderStateNature());
        assertTrue(decision.shouldPersist());
        assertFalse(decision.shouldRaiseAbnormal());
    }

    @Test
    void asyncFailedShouldBeIgnoredAfterSuccess() {
        OrderWebhookStateDecision decision = stateMachine.evaluate(
                BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED,
                OrderStatus.PAID,
                PaymentStatus.SUCCESS
        );

        assertEquals(TransitionClass.IGNORE_TRANSITION, decision.transitionClass());
        assertFalse(decision.shouldPersist());
        assertFalse(decision.shouldRaiseAbnormal());
    }

    @Test
    void expiredShouldBeIllegalAgainstCanceledOrder() {
        OrderWebhookStateDecision decision = stateMachine.evaluate(
                BusinessEventType.CHECKOUT_SESSION_EXPIRED,
                OrderStatus.CANCELLED,
                PaymentStatus.FAILED
        );

        assertEquals(TransitionClass.ILLEGAL_TRANSITION, decision.transitionClass());
        assertTrue(decision.shouldRaiseAbnormal());
    }
}
