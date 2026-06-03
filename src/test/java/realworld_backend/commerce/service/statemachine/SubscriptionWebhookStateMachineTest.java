package realworld_backend.commerce.service.statemachine;

import org.junit.jupiter.api.Test;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionWebhookStateMachineTest {

    private final SubscriptionWebhookStateMachine stateMachine = new SubscriptionWebhookStateMachine();

    @Test
    void checkoutCompletedShouldMovePendingToPaying() {
        SubscriptionWebhookStateDecision decision = stateMachine.evaluate(
                BusinessEventType.CHECKOUT_SESSION_COMPLETED,
                SubscriptionStatus.PENDING
        );

        assertEquals(TransitionClass.LEGAL_TRANSITION, decision.transitionClass());
        assertEquals(SubscriptionStatus.PAYING, decision.nextStatus());
        assertEquals(StateNature.INTERMEDIATE_STATE, decision.currentStateNature());
        assertEquals(StateNature.INTERMEDIATE_STATE, decision.nextStateNature());
        assertTrue(decision.shouldPersist());
        assertTrue(decision.shouldAppendHistory());
    }

    @Test
    void invoiceFailedShouldBeBillingFactOnly() {
        SubscriptionWebhookStateDecision decision = stateMachine.evaluate(
                BusinessEventType.INVOICE_PAYMENT_FAILED,
                SubscriptionStatus.ACTIVE
        );

        assertEquals(TransitionClass.LEGAL_TRANSITION, decision.transitionClass());
        assertEquals(SubscriptionStatus.ACTIVE, decision.nextStatus());
        assertEquals(StateNature.RECOVERABLE_STATE, decision.currentStateNature());
        assertEquals(StateNature.RECOVERABLE_STATE, decision.nextStateNature());
        assertFalse(decision.shouldPersist());
    }

    @Test
    void checkoutExpiredShouldMovePendingToCheckoutExpired() {
        SubscriptionWebhookStateDecision decision = stateMachine.evaluate(
                BusinessEventType.CHECKOUT_SESSION_EXPIRED,
                SubscriptionStatus.PENDING
        );

        assertEquals(TransitionClass.LEGAL_TRANSITION, decision.transitionClass());
        assertEquals(SubscriptionStatus.CHECKOUT_EXPIRED, decision.nextStatus());
        assertEquals(StateNature.INTERMEDIATE_STATE, decision.currentStateNature());
        assertEquals(StateNature.TERMINAL_STATE, decision.nextStateNature());
        assertTrue(decision.shouldPersist());
        assertTrue(decision.shouldAppendHistory());
    }

    @Test
    void checkoutExpiredShouldIgnoreAlreadyExpiredSubscription() {
        SubscriptionWebhookStateDecision decision = stateMachine.evaluate(
                BusinessEventType.CHECKOUT_SESSION_EXPIRED,
                SubscriptionStatus.CHECKOUT_EXPIRED
        );

        assertEquals(TransitionClass.IGNORE_TRANSITION, decision.transitionClass());
        assertEquals(SubscriptionStatus.CHECKOUT_EXPIRED, decision.nextStatus());
        assertFalse(decision.shouldPersist());
    }

    @Test
    void providerStatusTransitionShouldMovePendingToActive() {
        SubscriptionWebhookStateDecision decision = stateMachine.evaluateProviderStatusTransition(
                BusinessEventType.SUBSCRIPTION_UPDATED,
                SubscriptionStatus.PENDING,
                SubscriptionStatus.ACTIVE
        );

        assertEquals(TransitionClass.LEGAL_TRANSITION, decision.transitionClass());
        assertEquals(SubscriptionStatus.ACTIVE, decision.nextStatus());
        assertTrue(decision.shouldPersist());
        assertTrue(decision.shouldAppendHistory());
    }

    @Test
    void deletedShouldCancelPastDueSubscription() {
        SubscriptionWebhookStateDecision decision = stateMachine.evaluate(
                BusinessEventType.SUBSCRIPTION_DELETED,
                SubscriptionStatus.PAST_DUE
        );

        assertEquals(TransitionClass.LEGAL_TRANSITION, decision.transitionClass());
        assertEquals(SubscriptionStatus.CANCELED, decision.nextStatus());
        assertEquals(StateNature.RECOVERABLE_STATE, decision.currentStateNature());
        assertEquals(StateNature.TERMINAL_STATE, decision.nextStateNature());
    }

    @Test
    void providerStatusTransitionShouldIgnoreWhenAlreadyAtTargetStatus() {
        SubscriptionWebhookStateDecision decision = stateMachine.evaluateProviderStatusTransition(
                BusinessEventType.SUBSCRIPTION_UPDATED,
                SubscriptionStatus.CANCELED,
                SubscriptionStatus.CANCELED
        );

        assertEquals(TransitionClass.IGNORE_TRANSITION, decision.transitionClass());
        assertFalse(decision.shouldPersist());
    }

    @Test
    void providerStatusTransitionShouldTreatCanceledAsSticky() {
        SubscriptionWebhookStateDecision decision = stateMachine.evaluateProviderStatusTransition(
                BusinessEventType.SUBSCRIPTION_UPDATED,
                SubscriptionStatus.CANCELED,
                SubscriptionStatus.ACTIVE
        );

        assertEquals(TransitionClass.RETRYABLE_ILLEGAL_TRANSITION, decision.transitionClass());
        assertTrue(decision.shouldRaiseAbnormal());
    }

    @Test
    void checkoutFailedShouldMovePayingToCheckoutFail() {
        SubscriptionWebhookStateDecision decision = stateMachine.evaluate(
                BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED,
                SubscriptionStatus.PAYING
        );

        assertEquals(TransitionClass.LEGAL_TRANSITION, decision.transitionClass());
        assertEquals(SubscriptionStatus.CHECKOUT_FAIL, decision.nextStatus());
        assertTrue(decision.shouldPersist());
    }

    @Test
    void checkoutCompletedShouldRecoverCheckoutFailBackToPaying() {
        SubscriptionWebhookStateDecision decision = stateMachine.evaluate(
                BusinessEventType.CHECKOUT_SESSION_COMPLETED,
                SubscriptionStatus.CHECKOUT_FAIL
        );

        assertEquals(TransitionClass.LEGAL_TRANSITION, decision.transitionClass());
        assertEquals(SubscriptionStatus.PAYING, decision.nextStatus());
        assertTrue(decision.shouldPersist());
    }

    @Test
    void providerStatusTransitionShouldAllowPastDueToActive() {
        SubscriptionWebhookStateDecision decision = stateMachine.evaluateProviderStatusTransition(
                BusinessEventType.SUBSCRIPTION_UPDATED,
                SubscriptionStatus.PAST_DUE,
                SubscriptionStatus.ACTIVE
        );

        assertEquals(TransitionClass.LEGAL_TRANSITION, decision.transitionClass());
        assertEquals(SubscriptionStatus.ACTIVE, decision.nextStatus());
    }

    @Test
    void providerStatusTransitionShouldRejectActiveToTrialing() {
        SubscriptionWebhookStateDecision decision = stateMachine.evaluateProviderStatusTransition(
                BusinessEventType.SUBSCRIPTION_UPDATED,
                SubscriptionStatus.ACTIVE,
                SubscriptionStatus.TRIALING
        );

        assertEquals(TransitionClass.ILLEGAL_TRANSITION, decision.transitionClass());
        assertTrue(decision.shouldRaiseAbnormal());
    }

    @Test
    void providerStatusTransitionShouldRejectPausedToTrialing() {
        SubscriptionWebhookStateDecision decision = stateMachine.evaluateProviderStatusTransition(
                BusinessEventType.SUBSCRIPTION_UPDATED,
                SubscriptionStatus.PAUSED,
                SubscriptionStatus.TRIALING
        );

        assertEquals(TransitionClass.ILLEGAL_TRANSITION, decision.transitionClass());
        assertTrue(decision.shouldRaiseAbnormal());
    }

    @Test
    void providerStatusTransitionShouldRejectPastDueToTrialing() {
        SubscriptionWebhookStateDecision decision = stateMachine.evaluateProviderStatusTransition(
                BusinessEventType.SUBSCRIPTION_UPDATED,
                SubscriptionStatus.PAST_DUE,
                SubscriptionStatus.TRIALING
        );

        assertEquals(TransitionClass.ILLEGAL_TRANSITION, decision.transitionClass());
        assertTrue(decision.shouldRaiseAbnormal());
    }
}
