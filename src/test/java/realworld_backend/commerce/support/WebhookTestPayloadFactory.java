package realworld_backend.commerce.support;

import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.core.ProviderRawEvent;

/**
 * Builds webhook object payloads for local handler tests.
 * ProviderRawEvent.rawObjectJson stores the provider object body only, not the outer event envelope.
 */
public final class WebhookTestPayloadFactory {

    private WebhookTestPayloadFactory() {
    }

    public static ProviderRawEvent checkoutCompletedPaymentRawEvent(
            String eventId,
            String sessionId,
            String orderNo,
            String paymentIntentId,
            long amountTotal
    ) {
        return checkoutSessionRawEvent(
                eventId,
                BusinessEventType.CHECKOUT_SESSION_COMPLETED,
                "checkout.session.completed",
                sessionId,
                "payment",
                "paid",
                orderNo,
                paymentIntentId,
                amountTotal
        );
    }

    public static ProviderRawEvent checkoutCompletedPaymentRawEventWithoutMetadata(
            String eventId,
            String sessionId,
            String paymentIntentId,
            long amountTotal
    ) {
        return checkoutSessionRawEvent(
                eventId,
                BusinessEventType.CHECKOUT_SESSION_COMPLETED,
                "checkout.session.completed",
                sessionId,
                "payment",
                "paid",
                null,
                paymentIntentId,
                amountTotal
        );
    }

    public static ProviderRawEvent checkoutAsyncPaymentFailedRawEvent(
            String eventId,
            String sessionId,
            String orderNo,
            long amountTotal
    ) {
        return checkoutSessionRawEvent(
                eventId,
                BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED,
                "checkout.session.async_payment_failed",
                sessionId,
                "payment",
                "unpaid",
                orderNo,
                null,
                amountTotal
        );
    }

    public static ProviderRawEvent checkoutCompletedSubscriptionRawEvent(
            String eventId,
            String sessionId,
            String subscriptionNo,
            String providerSubscriptionId,
            String providerCustomerId
    ) {
        String rawObjectJson = """
                {
                  "id": "%s",
                  "status": "complete",
                  "payment_status": "paid",
                  "mode": "subscription",
                  "amount_total": 3000,
                  "currency": "usd",
                  "customer": "%s",
                  "subscription": "%s",
                  "metadata": {
                    "subscriptionNo": "%s",
                    "orderNo": "%s",
                    "userId": "1",
                    "planCode": "PRO",
                    "product": "Pro Plan"
                  }
                }
                """.formatted(sessionId, providerCustomerId, providerSubscriptionId, subscriptionNo, subscriptionNo);

        return buildRawEvent(
                eventId,
                BusinessEventType.CHECKOUT_SESSION_COMPLETED,
                "checkout.session.completed",
                rawObjectJson
        );
    }

    public static ProviderRawEvent subscriptionCreatedRawEvent(
            String eventId,
            String providerSubscriptionId,
            String subscriptionNo
    ) {
        String rawObjectJson = """
                {
                  "id": "%s",
                  "object": "subscription",
                  "status": "active",
                  "customer": "cus_123",
                  "current_period_start": 1779248316,
                  "current_period_end": 1781840316,
                  "cancel_at_period_end": false,
                  "metadata": {
                    "subscriptionNo": "%s",
                    "userId": "1",
                    "product": "Pro Plan"
                  }
                }
                """.formatted(providerSubscriptionId, subscriptionNo);

        return buildRawEvent(
                eventId,
                BusinessEventType.SUBSCRIPTION_CREATED,
                "customer.subscription.created",
                rawObjectJson
        );
    }

    public static ProviderRawEvent subscriptionDeletedRawEvent(
            String eventId,
            String providerSubscriptionId,
            String subscriptionNo
    ) {
        String rawObjectJson = """
                {
                  "id": "%s",
                  "object": "subscription",
                  "status": "canceled",
                  "customer": "cus_123",
                  "current_period_start": 1779248316,
                  "current_period_end": 1781840316,
                  "cancel_at_period_end": true,
                  "canceled_at": 1779249316,
                  "metadata": {
                    "subscriptionNo": "%s",
                    "userId": "1",
                    "product": "Pro Plan"
                  }
                }
                """.formatted(providerSubscriptionId, subscriptionNo);

        return buildRawEvent(
                eventId,
                BusinessEventType.SUBSCRIPTION_DELETED,
                "customer.subscription.deleted",
                rawObjectJson
        );
    }

    public static ProviderRawEvent subscriptionUpdatedRawEvent(
            String eventId,
            String providerSubscriptionId,
            String subscriptionNo,
            String providerStatus
    ) {
        String rawObjectJson = """
                {
                  "id": "%s",
                  "object": "subscription",
                  "status": "%s",
                  "customer": "cus_123",
                  "current_period_start": 1779248316,
                  "current_period_end": 1781840316,
                  "cancel_at_period_end": false,
                  "metadata": {
                    "subscriptionNo": "%s",
                    "userId": "1",
                    "product": "Pro Plan"
                  }
                }
                """.formatted(providerSubscriptionId, providerStatus, subscriptionNo);

        return buildRawEvent(
                eventId,
                BusinessEventType.SUBSCRIPTION_UPDATED,
                "customer.subscription.updated",
                rawObjectJson
        );
    }

    public static ProviderRawEvent subscriptionUpdatedRawEventWithItemPeriods(
            String eventId,
            String providerSubscriptionId,
            String subscriptionNo,
            String providerStatus,
            long itemPeriodStart,
            long itemPeriodEnd
    ) {
        String rawObjectJson = """
                {
                  "id": "%s",
                  "object": "subscription",
                  "status": "%s",
                  "customer": "cus_123",
                  "cancel_at_period_end": false,
                  "items": {
                    "data": [
                      {
                        "id": "si_123",
                        "current_period_start": %d,
                        "current_period_end": %d,
                        "quantity": 1,
                        "price": {
                          "id": "price_123",
                          "product": "prod_123",
                          "unit_amount": 3000,
                          "recurring": {
                            "interval": "month"
                          }
                        }
                      }
                    ]
                  },
                  "metadata": {
                    "subscriptionNo": "%s",
                    "userId": "1",
                    "product": "Pro Plan"
                  }
                }
                """.formatted(providerSubscriptionId, providerStatus, itemPeriodStart, itemPeriodEnd, subscriptionNo);

        return buildRawEvent(
                eventId,
                BusinessEventType.SUBSCRIPTION_UPDATED,
                "customer.subscription.updated",
                rawObjectJson
        );
    }

    public static ProviderRawEvent invoicePaymentFailedRawEvent(
            String eventId,
            String invoiceId,
            String subscriptionNo
    ) {
        String rawObjectJson = """
                {
                  "id": "%s",
                  "object": "invoice",
                  "status": "open",
                  "paid": false,
                  "customer": "cus_123",
                  "subscription": "sub_123",
                  "metadata": {
                    "subscriptionNo": "%s"
                  },
                  "last_payment_error": {
                    "message": "card declined"
                  }
                }
                """.formatted(invoiceId, subscriptionNo);

        return buildRawEvent(
                eventId,
                BusinessEventType.INVOICE_PAYMENT_FAILED,
                "invoice.payment_failed",
                rawObjectJson
        );
    }

    public static ProviderRawEvent invoicePaymentSucceededRawEvent(
            String eventId,
            String invoiceId,
            String subscriptionNo
    ) {
        String rawObjectJson = """
                {
                  "id": "%s",
                  "object": "invoice",
                  "status": "paid",
                  "paid": true,
                  "customer": "cus_123",
                  "subscription": "sub_123",
                  "subscription_details": {
                    "metadata": {
                      "subscriptionNo": "%s"
                    }
                  }
                }
                """.formatted(invoiceId, subscriptionNo);

        return buildRawEvent(
                eventId,
                BusinessEventType.INVOICE_PAYMENT_SUCCEEDED,
                "invoice.payment_succeeded",
                rawObjectJson
        );
    }

    public static ProviderRawEvent invoicePaymentActionRequiredRawEvent(
            String eventId,
            String invoiceId,
            String subscriptionNo
    ) {
        String rawObjectJson = """
                {
                  "id": "%s",
                  "object": "invoice",
                  "status": "open",
                  "paid": false,
                  "customer": "cus_123",
                  "subscription": "sub_123",
                  "subscription_details": {
                    "metadata": {
                      "subscriptionNo": "%s"
                    }
                  }
                }
                """.formatted(invoiceId, subscriptionNo);

        return buildRawEvent(
                eventId,
                BusinessEventType.INVOICE_PAYMENT_ACTION_REQUIRED,
                "invoice.payment_action_required",
                rawObjectJson
        );
    }

    private static ProviderRawEvent checkoutSessionRawEvent(
            String eventId,
            BusinessEventType type,
            String rawType,
            String sessionId,
            String mode,
            String paymentStatus,
            String orderNo,
            String paymentIntentId,
            long amountTotal
    ) {
        String metadataJson = orderNo == null
                ? "{}"
                : """
                  {
                    "orderNo": "%s",
                    "userId": "1",
                    "product": "2"
                  }
                  """.formatted(orderNo).strip();

        String paymentIntentJson = paymentIntentId == null
                ? "null"
                : "\"%s\"".formatted(paymentIntentId);

        String rawObjectJson = """
                {
                  "id": "%s",
                  "status": "complete",
                  "payment_status": "%s",
                  "mode": "%s",
                  "amount_total": %d,
                  "currency": "usd",
                  "payment_intent": %s,
                  "metadata": %s
                }
                """.formatted(sessionId, paymentStatus, mode, amountTotal, paymentIntentJson, metadataJson);

        return buildRawEvent(eventId, type, rawType, rawObjectJson);
    }

    private static ProviderRawEvent buildRawEvent(
            String eventId,
            BusinessEventType type,
            String rawType,
            String rawObjectJson
    ) {
        return ProviderRawEvent.builder()
                .provider("STRIPE")
                .eventId(eventId)
                .type(type)
                .rawType(rawType)
                .rawObjectJson(rawObjectJson)
                .created(1779248320L)
                .livemode(false)
                .build();
    }
}
