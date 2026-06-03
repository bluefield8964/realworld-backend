package realworld_backend.commerce.service.webhook.core;


import realworld_backend.commerce.event.BusinessEventType;

public class EventAnticorruptionLayer {

    public static BusinessEventType convertProviderEvent(String eventType) {
        return switch (eventType) {

            // payment_intent.* is not handled by checkout-session parser flow.
            // Keep it out of order handler to avoid parsing mismatch (pi_* vs cs_*).
            case "payment_intent.payment_failed" -> BusinessEventType.UNKNOWN_EVENT;

            case "checkout.session.completed" -> BusinessEventType.CHECKOUT_SESSION_COMPLETED;
            case "checkout.session.async_payment_failed" -> BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED;
            case "checkout.session.expired" -> BusinessEventType.CHECKOUT_SESSION_EXPIRED;

            case "refund.created" -> BusinessEventType.REFUND_CREATED;
            case "refund.updated" -> BusinessEventType.REFUND_UPDATED;
            case "refund.failed" -> BusinessEventType.REFUND_FAILED;
            case "charge.refunded" -> BusinessEventType.REFUND_SUCCEEDED;

            //------------customer------------
            case "customer.subscription.created" -> BusinessEventType.SUBSCRIPTION_CREATED;
            case "customer.subscription.updated" -> BusinessEventType.SUBSCRIPTION_UPDATED;
            case "customer.subscription.paused" -> BusinessEventType.SUBSCRIPTION_PAUSED;
            case "customer.subscription.resumed" -> BusinessEventType.SUBSCRIPTION_RESUMED;
            case "customer.subscription.deleted" -> BusinessEventType.SUBSCRIPTION_DELETED;
            case "customer.subscription.trial_will_end" -> BusinessEventType.CUSTOMER_SUBSCRIPTION_TRIAL_WILL_END;

            //-----------invoice-------------
            case "invoice.created" -> BusinessEventType.INVOICE_CREATED;
            case "invoice.updated" -> BusinessEventType.INVOICE_UPDATED;
            case "invoice.finalized" -> BusinessEventType.INVOICE_FINALIZED;
            case "invoice.finalization_failed" -> BusinessEventType.INVOICE_FINALIZATION_FAILED;
            case "invoice.paid" -> BusinessEventType.INVOICE_PAID;
            case "invoice.payment_action_required" -> BusinessEventType.INVOICE_PAYMENT_ACTION_REQUIRED;
            case "invoice.payment_succeeded" -> BusinessEventType.INVOICE_PAYMENT_SUCCEEDED;

            case "invoice.payment_failed" -> BusinessEventType.INVOICE_PAYMENT_FAILED;

            default -> BusinessEventType.UNKNOWN_EVENT;
        };
    }

}
