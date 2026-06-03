package realworld_backend.commerce.service.webhook.handler.invoice;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.webhook.core.WebhookHandler;
import realworld_backend.commerce.service.subscription.SubscriptionWebhookService;

@Component
@RequiredArgsConstructor
public class InvoicePaymentActionRequiredHandler implements WebhookHandler {
    private final SubscriptionWebhookService subscriptionWebhookService;

    @Override
    public BusinessEventType supports() {
        return BusinessEventType.INVOICE_PAYMENT_ACTION_REQUIRED;
    }

    @Override
    public void handle(WebhookContext ctx) {
        subscriptionWebhookService.handleInvoicePaymentActionRequired(ctx);
    }
}
