package realworld_backend.commerce.service.webhook.handler;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.service.webhook.core.WebhookHandler;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Indexes webhook handlers by normalized business event type.
 */
@Component
@RequiredArgsConstructor
public class WebhookHandlerRouter {
    private final List<WebhookHandler> handlers;
    private Map<BusinessEventType, WebhookHandler> index;

    @PostConstruct
    void init() {
        index = handlers.stream()
                .collect(Collectors.toMap(WebhookHandler::supports, Function.identity()));
    }

    public WebhookHandler get(BusinessEventType type) {
        return index.get(type);
    }
}
