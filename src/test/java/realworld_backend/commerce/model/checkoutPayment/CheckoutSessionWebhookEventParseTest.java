package realworld_backend.commerce.model.checkoutPayment;

import org.junit.jupiter.api.Test;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.support.WebhookTestPayloadFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckoutSessionWebhookEventParseTest {

    @Test
    void parseProviderRawEventShouldKeepBusinessMetadataAndIds() {
        ProviderRawEvent rawEvent = WebhookTestPayloadFactory.checkoutCompletedPaymentRawEvent(
                "evt_checkout_parse_1",
                "cs_parse_123",
                "order_123",
                "pi_123",
                3000L
        );

        CheckoutSessionWebhookEvent event = CheckoutSessionWebhookEvent.parseProviderRawEvent(rawEvent);

        assertEquals("cs_parse_123", event.getObject().getId());
        assertEquals("payment", event.getObject().getMode());
        assertEquals("paid", event.getObject().getPaymentStatus());
        assertEquals("order_123", event.getObject().getMetadata().get("orderNo"));
        assertEquals("1", event.getObject().getMetadata().get("userId"));
        assertEquals("pi_123", event.getObject().getPaymentIntent());
        assertEquals(3000L, event.getObject().getAmountTotal());
    }

    @Test
    void parseProviderRawEventShouldReadExpandableFieldsFromExpandedObjects() {
        ProviderRawEvent rawEvent = ProviderRawEvent.builder()
                .provider("STRIPE")
                .eventId("evt_checkout_parse_2")
                .type(realworld_backend.commerce.event.BusinessEventType.CHECKOUT_SESSION_COMPLETED)
                .rawType("checkout.session.completed")
                .rawObjectJson("""
                        {
                          "id": "cs_sub_parse_1",
                          "status": "complete",
                          "payment_status": "paid",
                          "mode": "subscription",
                          "customer": {
                            "id": "cus_expanded_1"
                          },
                          "subscription": {
                            "id": "sub_expanded_1"
                          },
                          "setup_intent": {
                            "id": "seti_expanded_1"
                          },
                          "metadata": {
                            "subscriptionNo": "sub_no_123"
                          }
                        }
                        """)
                .created(1779248320L)
                .livemode(false)
                .build();

        CheckoutSessionWebhookEvent event = CheckoutSessionWebhookEvent.parseProviderRawEvent(rawEvent);

        assertEquals("cus_expanded_1", event.getObject().getCustomer());
        assertEquals("sub_expanded_1", event.getObject().getSubscription());
        assertEquals("seti_expanded_1", event.getObject().getSetupIntent());
        assertEquals("sub_no_123", event.getObject().getMetadata().get("subscriptionNo"));
    }
}
