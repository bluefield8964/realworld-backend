package realworld_backend.commerce.service.core;

import realworld_backend.commerce.event.BusinessEventType;

public interface WebhookHandler {
    BusinessEventType supports();
    void handle(WebhookContext ctx) throws Exception;
}
