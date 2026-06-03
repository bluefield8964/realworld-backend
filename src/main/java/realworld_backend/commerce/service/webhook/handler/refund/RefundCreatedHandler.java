package realworld_backend.commerce.service.webhook.handler.refund;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.service.webhook.WebhookAckService;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.webhook.core.WebhookHandler;

@Component
@RequiredArgsConstructor
public class RefundCreatedHandler implements WebhookHandler {
    private final WebhookAckService webhookAckService;

    @Override
    public BusinessEventType supports() {
        return BusinessEventType.REFUND_CREATED;
    }

    @Override
    public void handle(WebhookContext ctx) {
        webhookAckService.accept(ctx, "refund.created");
    }
}
