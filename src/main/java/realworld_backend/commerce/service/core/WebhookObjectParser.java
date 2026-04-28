package realworld_backend.commerce.service.core;

import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.core.ProviderRawEvent;

public interface WebhookObjectParser <T> {
    BusinessEventType supports();
    T parse(ProviderRawEvent e);
}
