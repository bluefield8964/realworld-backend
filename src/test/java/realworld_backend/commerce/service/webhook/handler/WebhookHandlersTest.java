package realworld_backend.commerce.service.webhook.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.service.webhook.CheckoutSessionWebhookService;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.webhook.handler.checkout.CheckoutCompletedHandler;
import realworld_backend.commerce.service.webhook.handler.checkout.CheckoutPaymentFailedHandler;
import realworld_backend.commerce.service.webhook.handler.invoice.InvoicePaymentFailedHandler;
import realworld_backend.commerce.service.webhook.handler.invoice.InvoicePaymentSucceededHandler;
import realworld_backend.commerce.service.webhook.handler.subscription.SubscriptionCreatedHandler;
import realworld_backend.commerce.service.webhook.handler.subscription.SubscriptionDeletedHandler;
import realworld_backend.commerce.service.subscription.SubscriptionWebhookService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebhookHandlersTest {

    @Mock
    private CheckoutSessionWebhookService checkoutSessionWebhookService;
    @Mock
    private SubscriptionWebhookService subscriptionWebhookService;

    @Test
    void checkoutCompletedHandlerShouldSupportCompletedEventAndDelegate() throws Exception {
        CheckoutCompletedHandler handler = new CheckoutCompletedHandler(checkoutSessionWebhookService);
        WebhookContext ctx = WebhookContext.builder().eventId("evt_1").build();

        assertEquals(BusinessEventType.CHECKOUT_SESSION_COMPLETED, handler.supports());
        assertDoesNotThrow(() -> handler.handle(ctx));
        verify(checkoutSessionWebhookService).handleCheckoutSessionCompleted(ctx);
    }

    @Test
    void checkoutPaymentFailedHandlerShouldSupportAsyncFailedEventAndDelegate() throws Exception {
        CheckoutPaymentFailedHandler handler = new CheckoutPaymentFailedHandler(checkoutSessionWebhookService);
        WebhookContext ctx = WebhookContext.builder().eventId("evt_2").build();

        assertEquals(BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED, handler.supports());
        assertDoesNotThrow(() -> handler.handle(ctx));
        verify(checkoutSessionWebhookService).handleCheckoutSessionAsyncPaymentFailed(ctx);
    }

    @Test
    void subscriptionCreatedHandlerShouldSupportCreatedEventAndDelegate() throws Exception {
        SubscriptionCreatedHandler handler = new SubscriptionCreatedHandler(subscriptionWebhookService);
        WebhookContext ctx = WebhookContext.builder().eventId("evt_3").build();

        assertEquals(BusinessEventType.SUBSCRIPTION_CREATED, handler.supports());
        assertDoesNotThrow(() -> handler.handle(ctx));
        verify(subscriptionWebhookService).handleSubscriptionCreatedEvent(ctx);
    }

    @Test
    void subscriptionDeletedHandlerShouldSupportDeletedEventAndDelegate() throws Exception {
        SubscriptionDeletedHandler handler = new SubscriptionDeletedHandler(subscriptionWebhookService);
        WebhookContext ctx = WebhookContext.builder().eventId("evt_4").build();

        assertEquals(BusinessEventType.SUBSCRIPTION_DELETED, handler.supports());
        assertDoesNotThrow(() -> handler.handle(ctx));
        verify(subscriptionWebhookService).handleSubscriptionDeletedEvent(ctx);
    }

    @Test
    void invoicePaymentFailedHandlerShouldSupportInvoiceFailedEventAndDelegate() throws Exception {
        InvoicePaymentFailedHandler handler = new InvoicePaymentFailedHandler(subscriptionWebhookService);
        WebhookContext ctx = WebhookContext.builder().eventId("evt_5").build();

        assertEquals(BusinessEventType.INVOICE_PAYMENT_FAILED, handler.supports());
        assertDoesNotThrow(() -> handler.handle(ctx));
        verify(subscriptionWebhookService).handleInvoicePaymentFailed(ctx);
    }

    @Test
    void invoicePaymentSucceededHandlerShouldSupportInvoiceSucceededEventAndDelegate() throws Exception {
        InvoicePaymentSucceededHandler handler = new InvoicePaymentSucceededHandler(subscriptionWebhookService);
        WebhookContext ctx = WebhookContext.builder().eventId("evt_6").build();

        assertEquals(BusinessEventType.INVOICE_PAYMENT_SUCCEEDED, handler.supports());
        assertDoesNotThrow(() -> handler.handle(ctx));
        verify(subscriptionWebhookService).handleInvoicePaymentSucceeded(ctx);
    }
}
