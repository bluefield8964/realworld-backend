package realworld_backend.commerce.service.webhook.handler.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.service.subscription.SubscriptionWebhookService;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.webhook.core.WebhookHandler;

/**
 * Routes customer.subscription.updated into the lifecycle-owned subscription path.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionUpdatedHandler implements WebhookHandler {
    private final SubscriptionWebhookService subscriptionWebhookService;

    @Override
    public BusinessEventType supports() {
        return BusinessEventType.SUBSCRIPTION_UPDATED;
    }

    @Override
    public void handle(WebhookContext ctx) {
        subscriptionWebhookService.handleSubscriptionLifecycleUpdatedEvent(ctx);
    }
}
