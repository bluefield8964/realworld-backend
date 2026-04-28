package realworld_backend.commerce.service.core;


import realworld_backend.commerce.event.BusinessEventType;

public class EventAnticorruptionLayer {

    public static BusinessEventType convertStripeEvent(String eventType, String rawObjectJson) {
        return switch (eventType) {

            // payment_intent.* is not handled by checkout-session parser flow.
            // Keep it out of order handler to avoid parsing mismatch (pi_* vs cs_*).
            case "payment_intent.payment_failed" -> BusinessEventType.UNKNOWN_EVENT;

            case "checkout.session.completed" -> BusinessEventType.CHECKOUT_SESSION_COMPLETED;
            case "checkout.session.async_payment_failed" -> BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED;
            case "checkout.session.expired" -> BusinessEventType.CHECKOUT_SESSION_EXPIRED;

            case "charge.refunded" -> BusinessEventType.REFUND_SUCCEEDED;

            //------------customer------------
            case "customer.subscription.created" -> BusinessEventType.SUBSCRIPTION_CREATED;

            case "customer.subscription.deleted" -> BusinessEventType.SUBSCRIPTION_DELETED;
            case "customer.subscription.trial_will_end" -> BusinessEventType.CUSTOMER_SUBSCRIPTION_TRIAL_WILL_END;

            //-----------invoice-------------
            case "invoice.paid" -> BusinessEventType.INVOICE_PAID;
            case "invoice.payment_succeeded" -> BusinessEventType.INVOICE_PAYMENT_SUCCEEDED;

            case "invoice.payment_failed" -> BusinessEventType.INVOICE_PAYMENT_FAILED;

            default -> BusinessEventType.UNKNOWN_EVENT;
        };
    }

}

