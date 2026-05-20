package realworld_backend.commerce.service.impl.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.service.CheckoutSessionWebhookService;
import realworld_backend.commerce.service.core.WebhookContext;
import realworld_backend.commerce.service.core.WebhookHandler;

@Component
@RequiredArgsConstructor
public class CheckoutCompletedHandler implements WebhookHandler {
    private final CheckoutSessionWebhookService checkoutSessionWebhookService;

    @Override
    public BusinessEventType supports() {
        return BusinessEventType.CHECKOUT_SESSION_COMPLETED;
    }

    @Override
    public void handle(WebhookContext ctx) throws Exception {
        checkoutSessionWebhookService.handleCheckoutSessionCompleted(ctx);
    }
}
