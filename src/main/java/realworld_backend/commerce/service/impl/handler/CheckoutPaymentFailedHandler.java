package realworld_backend.commerce.service.impl.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.service.CheckoutSessionWebhookService;
import realworld_backend.commerce.service.core.WebhookContext;
import realworld_backend.commerce.service.core.WebhookHandler;

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
