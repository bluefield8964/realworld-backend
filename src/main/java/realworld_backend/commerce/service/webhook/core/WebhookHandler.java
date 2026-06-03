package realworld_backend.commerce.service.webhook.core;

import realworld_backend.commerce.event.BusinessEventType;

/**
 * Handles one normalized webhook business event type.
 */
public interface WebhookHandler {
    BusinessEventType supports();

    void handle(WebhookContext ctx) throws Exception;
}
