package realworld_backend.commerce.service.statemachine;

import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;

/**
 * State-machine decision for subscription webhook transitions.
 */
public record SubscriptionWebhookStateDecision(
        BusinessEventType eventType,
        SubscriptionStatus currentStatus,
        SubscriptionStatus nextStatus,
        StateNature currentStateNature,
        StateNature nextStateNature,
        TransitionClass transitionClass,
        boolean shouldPersist,
        boolean shouldAppendHistory,
        boolean shouldRaiseAbnormal,
        String reason
) {
}
