package realworld_backend.commerce.service.webhook.handler.checkout;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.service.webhook.CheckoutSessionWebhookService;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.webhook.core.WebhookHandler;

@Component
@RequiredArgsConstructor
public class CheckoutPaymentFailedHandler implements WebhookHandler {
    private final CheckoutSessionWebhookService checkoutSessionWebhookService;

    @Override
    public BusinessEventType supports() {
        return BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED;
    }

    @Override
    public void handle(WebhookContext ctx) throws Exception {
        checkoutSessionWebhookService.handleCheckoutSessionAsyncPaymentFailed(ctx);
    }
}
