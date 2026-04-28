package realworld_backend.commerce.service.impl.parser;


import org.springframework.stereotype.Component;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.model.subscription.SubscriptionWebhookEvent;
import realworld_backend.commerce.service.core.WebhookObjectParser;

@Component
public class SubscriptionWebhookParser implements WebhookObjectParser {

    @Override
    public BusinessEventType supports() {
        return BusinessEventType.SUBSCRIPTION;
    }

    @Override
    public SubscriptionWebhookEvent parse(ProviderRawEvent e) {
        return SubscriptionWebhookEvent.parseProviderRawEvent(e);
    }
}

