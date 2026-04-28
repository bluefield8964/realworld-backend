package realworld_backend.commerce.service.impl.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.service.subscription.SubscriptionWebhookService;
import realworld_backend.commerce.service.core.WebhookContext;
import realworld_backend.commerce.service.core.WebhookHandler;

@Component
@RequiredArgsConstructor
public class SubscriptionDeletedHandler implements WebhookHandler {
    private final SubscriptionWebhookService subscriptionWebhookService;

    @Override
    public BusinessEventType supports() {
        return BusinessEventType.SUBSCRIPTION_DELETED;
    }

    @Override
    public void handle(WebhookContext ctx) throws Exception {
        subscriptionWebhookService.handleSubscriptionDeletedEvent(ctx);
    }
}
