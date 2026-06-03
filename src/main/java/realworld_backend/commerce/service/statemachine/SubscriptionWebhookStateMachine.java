package realworld_backend.commerce.service.statemachine;

import org.springframework.stereotype.Service;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;

import java.util.EnumSet;

/**
 * Centralizes subscription webhook transition semantics.
 * This service only classifies transitions and does not write state.
 */
@Service
public class SubscriptionWebhookStateMachine {

    public SubscriptionWebhookStateDecision evaluate(
            BusinessEventType eventType,
            SubscriptionStatus currentStatus
    ) {
        if (eventType == null || currentStatus == null) {
            return illegal(eventType, currentStatus, "missing current subscription state");
        }

        return switch (eventType) {
            case CHECKOUT_SESSION_COMPLETED -> evaluateCheckoutCompleted(currentStatus);
            case CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED -> evaluateCheckoutFailed(currentStatus);
            case CHECKOUT_SESSION_EXPIRED -> evaluateCheckoutExpired(currentStatus);
            case SUBSCRIPTION_CREATED -> evaluateSubscriptionCreated(currentStatus);
            case SUBSCRIPTION_DELETED -> evaluateSubscriptionDeleted(currentStatus);
            case SUBSCRIPTION_PAUSED -> evaluateSubscriptionPaused(currentStatus);
            case SUBSCRIPTION_RESUMED -> evaluateSubscriptionResumed(currentStatus);
            case SUBSCRIPTION_UPDATED -> legalNoStateChange(
                    eventType,
                    currentStatus,
                    false,
                    "subscription updated must use provider-status lifecycle evaluation"
            );
            case CUSTOMER_SUBSCRIPTION_TRIAL_WILL_END -> legalNoStateChange(
                    eventType,
                    currentStatus,
                    false,
                    "trial reminder snapshot only"
            );
            case INVOICE_PAYMENT_ACTION_REQUIRED -> legalNoStateChange(
                    eventType,
                    currentStatus,
                    false,
                    "invoice action required does not switch subscription state directly"
            );
            case INVOICE_PAYMENT_FAILED -> evaluateInvoicePaymentFailed(currentStatus);
            case INVOICE_PAYMENT_SUCCEEDED -> evaluateInvoicePaymentSucceeded(currentStatus);
            default -> ignore(eventType, currentStatus, "event not handled by subscription state machine");
        };
    }

    private SubscriptionWebhookStateDecision evaluateCheckoutCompleted(SubscriptionStatus currentStatus) {
        if (currentStatus == SubscriptionStatus.PAYING) {
            return ignore(BusinessEventType.CHECKOUT_SESSION_COMPLETED, currentStatus, "already paying");
        }
        if (EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING, SubscriptionStatus.PAST_DUE, SubscriptionStatus.PAUSED).contains(currentStatus)) {
            return ignore(BusinessEventType.CHECKOUT_SESSION_COMPLETED, currentStatus, "late checkout completed after activation");
        }
        if (EnumSet.of(SubscriptionStatus.PENDING, SubscriptionStatus.CHECKOUT_FAIL).contains(currentStatus)) {
            return legal(
                    BusinessEventType.CHECKOUT_SESSION_COMPLETED,
                    currentStatus,
                    SubscriptionStatus.PAYING,
                    true,
                    "subscription checkout completed and waiting provider settlement"
            );
        }
        if (EnumSet.of(SubscriptionStatus.CANCELED, SubscriptionStatus.CHECKOUT_EXPIRED).contains(currentStatus)) {
            return retryableIllegal(BusinessEventType.CHECKOUT_SESSION_COMPLETED, currentStatus,
                    "checkout completed reached terminal subscription state");
        }
        return illegal(BusinessEventType.CHECKOUT_SESSION_COMPLETED, currentStatus,
                "checkout completed does not match subscription state");
    }

    private SubscriptionWebhookStateDecision evaluateCheckoutFailed(SubscriptionStatus currentStatus) {
        if (currentStatus == SubscriptionStatus.CHECKOUT_FAIL) {
            return ignore(BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED, currentStatus,
                    "already checkout fail");
        }
        if (currentStatus == SubscriptionStatus.CHECKOUT_EXPIRED) {
            return ignore(BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED, currentStatus,
                    "late checkout failed after checkout expired");
        }
        if (EnumSet.of(
                SubscriptionStatus.ACTIVE,
                SubscriptionStatus.TRIALING,
                SubscriptionStatus.PAST_DUE,
                SubscriptionStatus.PAUSED,
                SubscriptionStatus.CANCELED
        ).contains(currentStatus)) {
            return ignore(BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED, currentStatus,
                    "late checkout failed after later progress");
        }
        if (EnumSet.of(SubscriptionStatus.PENDING, SubscriptionStatus.PAYING).contains(currentStatus)) {
            return legal(
                    BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED,
                    currentStatus,
                    SubscriptionStatus.CHECKOUT_FAIL,
                    true,
                    "subscription checkout failed"
            );
        }
        return illegal(BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED, currentStatus,
                "checkout failed does not match subscription state");
    }

    private SubscriptionWebhookStateDecision evaluateCheckoutExpired(SubscriptionStatus currentStatus) {
        if (currentStatus == SubscriptionStatus.CHECKOUT_EXPIRED) {
            return ignore(BusinessEventType.CHECKOUT_SESSION_EXPIRED, currentStatus, "already checkout expired");
        }
        if (currentStatus == SubscriptionStatus.CHECKOUT_FAIL) {
            return legal(
                    BusinessEventType.CHECKOUT_SESSION_EXPIRED,
                    currentStatus,
                    SubscriptionStatus.CHECKOUT_EXPIRED,
                    true,
                    "subscription checkout expired after retries"
            );
        }
        if (EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING, SubscriptionStatus.PAYING).contains(currentStatus)) {
            return ignore(BusinessEventType.CHECKOUT_SESSION_EXPIRED, currentStatus,
                    "late checkout expired after later progress");
        }
        if (currentStatus == SubscriptionStatus.PENDING) {
            return legal(
                    BusinessEventType.CHECKOUT_SESSION_EXPIRED,
                    currentStatus,
                    SubscriptionStatus.CHECKOUT_EXPIRED,
                    true,
                    "subscription checkout expired"
            );
        }
        return illegal(BusinessEventType.CHECKOUT_SESSION_EXPIRED, currentStatus,
                "checkout expired does not match subscription state");
    }

    private SubscriptionWebhookStateDecision evaluateSubscriptionCreated(SubscriptionStatus currentStatus) {
        if (EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING).contains(currentStatus)) {
            return ignore(BusinessEventType.SUBSCRIPTION_CREATED, currentStatus, "already activated");
        }
        if (currentStatus == SubscriptionStatus.CANCELED) {
            return retryableIllegal(BusinessEventType.SUBSCRIPTION_CREATED, currentStatus,
                    "created arrived after canceled");
        }
        return illegal(BusinessEventType.SUBSCRIPTION_CREATED, currentStatus,
                "subscription created does not match current state");
    }

    private SubscriptionWebhookStateDecision evaluateSubscriptionDeleted(SubscriptionStatus currentStatus) {
        if (currentStatus == SubscriptionStatus.CANCELED || currentStatus == SubscriptionStatus.CHECKOUT_EXPIRED || currentStatus == SubscriptionStatus.INITIAL_FAIL) {
            return ignore(BusinessEventType.SUBSCRIPTION_DELETED, currentStatus, "already canceled");
        }
        if (EnumSet.of(
                SubscriptionStatus.PENDING,
                SubscriptionStatus.PAYING,
                SubscriptionStatus.CHECKOUT_FAIL,
                SubscriptionStatus.ACTIVE,
                SubscriptionStatus.TRIALING,
                SubscriptionStatus.PAST_DUE,
                SubscriptionStatus.PAUSED
        ).contains(currentStatus)) {
            return legal(BusinessEventType.SUBSCRIPTION_DELETED, currentStatus, SubscriptionStatus.CANCELED, true,
                    "provider subscription canceled");
        }
        return illegal(BusinessEventType.SUBSCRIPTION_DELETED, currentStatus,
                "subscription deleted does not match current state");
    }

    private SubscriptionWebhookStateDecision evaluateSubscriptionPaused(SubscriptionStatus currentStatus) {
        if (currentStatus == SubscriptionStatus.PAUSED) {
            return ignore(BusinessEventType.SUBSCRIPTION_PAUSED, currentStatus, "already paused");
        }
        if (EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING, SubscriptionStatus.PAYING).contains(currentStatus)) {
            return legal(BusinessEventType.SUBSCRIPTION_PAUSED, currentStatus, SubscriptionStatus.PAUSED, true,
                    "subscription paused by provider");
        }
        return illegal(BusinessEventType.SUBSCRIPTION_PAUSED, currentStatus,
                "subscription paused does not match current state");
    }

    private SubscriptionWebhookStateDecision evaluateSubscriptionResumed(SubscriptionStatus currentStatus) {
        if (currentStatus == SubscriptionStatus.ACTIVE) {
            return ignore(BusinessEventType.SUBSCRIPTION_RESUMED, currentStatus, "already active");
        }
        if (currentStatus == SubscriptionStatus.PAUSED) {
            return legal(BusinessEventType.SUBSCRIPTION_RESUMED, currentStatus, SubscriptionStatus.ACTIVE, true,
                    "subscription resumed");
        }
        return illegal(BusinessEventType.SUBSCRIPTION_RESUMED, currentStatus,
                "subscription resumed does not match current state");
    }

    private SubscriptionWebhookStateDecision evaluateInvoicePaymentFailed(SubscriptionStatus currentStatus) {
        return legalNoStateChange(
                BusinessEventType.INVOICE_PAYMENT_FAILED,
                currentStatus,
                false,
                "invoice payment failed stored as billing fact only"
        );
    }

    private SubscriptionWebhookStateDecision evaluateInvoicePaymentSucceeded(SubscriptionStatus currentStatus) {
        return legalNoStateChange(
                BusinessEventType.INVOICE_PAYMENT_SUCCEEDED,
                currentStatus,
                false,
                "invoice payment succeeded stored as billing fact only"
        );
    }

    public SubscriptionWebhookStateDecision evaluateProviderStatusTransition(
            BusinessEventType eventType,
            SubscriptionStatus currentStatus,
            SubscriptionStatus targetStatus
    ) {
        if (eventType != BusinessEventType.SUBSCRIPTION_CREATED
                && eventType != BusinessEventType.SUBSCRIPTION_UPDATED) {
            return illegal(eventType, currentStatus, "provider status transition only supports subscription create/update");
        }
        if (targetStatus == null) {
            return legalNoStateChange(eventType, currentStatus, false, "provider status not mapped, snapshot only");
        }
        if (currentStatus == targetStatus) {
            return ignore(eventType, currentStatus, "subscription already at provider status");
        }
        if (currentStatus == SubscriptionStatus.CANCELED && targetStatus != SubscriptionStatus.CANCELED) {
            return retryableIllegal(eventType, currentStatus, "provider status reached canceled subscription");
        }
        if (EnumSet.of(SubscriptionStatus.INITIAL_FAIL, SubscriptionStatus.CHECKOUT_EXPIRED).contains(currentStatus)) {
            return illegal(eventType, currentStatus, "provider status reached terminal pre-activation subscription");
        }
        if (isAllowedLifecycleTransition(currentStatus, targetStatus)) {
            return legal(eventType, currentStatus, targetStatus, true, "provider subscription status synced");
        }
        return illegal(eventType, currentStatus, "provider status transition does not match current state");
    }

    private boolean isAllowedLifecycleTransition(
            SubscriptionStatus currentStatus,
            SubscriptionStatus targetStatus
    ) {
        if (currentStatus == null || targetStatus == null) {
            return false;
        }
        return switch (currentStatus) {
            case PENDING, PAYING, CHECKOUT_FAIL ->
                    EnumSet.of(
                            SubscriptionStatus.ACTIVE,
                            SubscriptionStatus.TRIALING,
                            SubscriptionStatus.PAST_DUE,
                            SubscriptionStatus.PAUSED,
                            SubscriptionStatus.CANCELED
                    ).contains(targetStatus);
            case TRIALING ->
                    EnumSet.of(
                            SubscriptionStatus.ACTIVE,
                            SubscriptionStatus.PAST_DUE,
                            SubscriptionStatus.PAUSED,
                            SubscriptionStatus.CANCELED
                    ).contains(targetStatus);
            case ACTIVE ->
                    EnumSet.of(
                            SubscriptionStatus.PAST_DUE,
                            SubscriptionStatus.PAUSED,
                            SubscriptionStatus.CANCELED
                    ).contains(targetStatus);
            case PAST_DUE ->
                    EnumSet.of(
                            SubscriptionStatus.ACTIVE,
                            SubscriptionStatus.PAUSED,
                            SubscriptionStatus.CANCELED
                    ).contains(targetStatus);
            case PAUSED ->
                    EnumSet.of(
                            SubscriptionStatus.ACTIVE,
                            SubscriptionStatus.CANCELED
                    ).contains(targetStatus);
            default -> false;
        };
    }

    private SubscriptionWebhookStateDecision legal(
            BusinessEventType eventType,
            SubscriptionStatus currentStatus,
            SubscriptionStatus nextStatus,
            boolean shouldAppendHistory,
            String reason
    ) {
        return new SubscriptionWebhookStateDecision(
                eventType,
                currentStatus,
                nextStatus,
                stateNatureOf(currentStatus),
                stateNatureOf(nextStatus),
                TransitionClass.LEGAL_TRANSITION,
                true,
                shouldAppendHistory,
                false,
                reason
        );
    }

    private SubscriptionWebhookStateDecision legalNoStateChange(
            BusinessEventType eventType,
            SubscriptionStatus currentStatus,
            boolean shouldAppendHistory,
            String reason
    ) {
        return new SubscriptionWebhookStateDecision(
                eventType,
                currentStatus,
                currentStatus,
                stateNatureOf(currentStatus),
                stateNatureOf(currentStatus),
                TransitionClass.LEGAL_TRANSITION,
                false,
                shouldAppendHistory,
                false,
                reason
        );
    }

    private SubscriptionWebhookStateDecision retryableIllegal(
            BusinessEventType eventType,
            SubscriptionStatus currentStatus,
            String reason
    ) {
        return new SubscriptionWebhookStateDecision(
                eventType,
                currentStatus,
                currentStatus,
                stateNatureOf(currentStatus),
                stateNatureOf(currentStatus),
                TransitionClass.RETRYABLE_ILLEGAL_TRANSITION,
                false,
                false,
                true,
                reason
        );
    }

    private SubscriptionWebhookStateDecision illegal(
            BusinessEventType eventType,
            SubscriptionStatus currentStatus,
            String reason
    ) {
        return new SubscriptionWebhookStateDecision(
                eventType,
                currentStatus,
                currentStatus,
                stateNatureOf(currentStatus),
                stateNatureOf(currentStatus),
                TransitionClass.ILLEGAL_TRANSITION,
                false,
                false,
                true,
                reason
        );
    }

    private SubscriptionWebhookStateDecision ignore(
            BusinessEventType eventType,
            SubscriptionStatus currentStatus,
            String reason
    ) {
        return new SubscriptionWebhookStateDecision(
                eventType,
                currentStatus,
                currentStatus,
                stateNatureOf(currentStatus),
                stateNatureOf(currentStatus),
                TransitionClass.IGNORE_TRANSITION,
                false,
                false,
                false,
                reason
        );
    }

    public StateNature stateNatureOf(SubscriptionStatus status) {
        if (status == null) {
            return StateNature.INTERMEDIATE_STATE;
        }
        return switch (status) {
            case INITIAL_FAIL, CANCELED, CHECKOUT_EXPIRED -> StateNature.TERMINAL_STATE;
            case ACTIVE, TRIALING, PAST_DUE, PAUSED, CHECKOUT_FAIL -> StateNature.RECOVERABLE_STATE;
            case CREATED, PENDING, PAYING -> StateNature.INTERMEDIATE_STATE;
        };
    }
}
