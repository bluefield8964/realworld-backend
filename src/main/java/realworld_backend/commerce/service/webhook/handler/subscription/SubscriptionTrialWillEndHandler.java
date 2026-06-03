package realworld_backend.commerce.service.webhook.handler.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.webhook.core.WebhookHandler;
import realworld_backend.commerce.service.subscription.SubscriptionWebhookService;

@Component
@RequiredArgsConstructor
/**
 * Routes trial reminder events through the subscription webhook facade.
 */
public class SubscriptionTrialWillEndHandler implements WebhookHandler {
    private final SubscriptionWebhookService subscriptionWebhookService;

    @Override
    public BusinessEventType supports() {
        return BusinessEventType.CUSTOMER_SUBSCRIPTION_TRIAL_WILL_END;
    }

    @Override
    public void handle(WebhookContext ctx) {
        subscriptionWebhookService.handleSubscriptionTrialWillEndEvent(ctx);
    }
}
