package realworld_backend.commerce.service.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.service.webhook.core.WebhookContext;

@Slf4j
@Service
public class WebhookAckService {

    public void accept(WebhookContext ctx, String capabilityName) {
        if (ctx == null) {
            log.info("Accepted placeholder webhook without context, capability={}", capabilityName);
            return;
        }
        log.info(
                "Accepted placeholder webhook: capability={}, eventId={}, eventType={}, trackingId={}, providerTrackingId={}",
                capabilityName,
                ctx.getEventId(),
                ctx.getEventType(),
                ctx.getTrackingId(),
                ctx.getProviderTrackingId()
        );
    }
}
