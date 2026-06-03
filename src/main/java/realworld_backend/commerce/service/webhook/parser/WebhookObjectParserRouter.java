package realworld_backend.commerce.service.webhook.parser;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.service.webhook.core.WebhookObjectParser;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Chooses the provider-object parser family for a business event type and casts the parsed result.
 */
@Service
@RequiredArgsConstructor
public class WebhookObjectParserRouter {
    private final List<WebhookObjectParser> parsers;
    private Map<BusinessEventType, WebhookObjectParser> byEventType;

    @PostConstruct
    void init() {
        byEventType = parsers.stream()
                .collect(Collectors.toMap(WebhookObjectParser::supports, c -> c));
    }

    public <T> T parseAs(ProviderRawEvent e, BusinessEventType type, Class<T> expectedType) {
        BusinessEventType parserType = normalize(type);
        WebhookObjectParser parser = byEventType.get(parserType);
        if (parser == null) {
            return null;
        }
        Object result = parser.parse(e);
        if (!expectedType.isInstance(result)) {
            throw new IllegalStateException("parser result type mismatch: " + type);
        }
        return expectedType.cast(result);
    }

    private BusinessEventType normalize(BusinessEventType type) {
        if (type == null) {
            return BusinessEventType.UNKNOWN_EVENT;
        }

        return switch (type) {
            case CHECKOUT_SESSION_COMPLETED,
                 CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED,
                 CHECKOUT_SESSION_EXPIRED,
                 ORDER_PAYMENT_COMPLETED,
                 ORDER_PAYMENT_FAILED,
                 ORDER_PAYMENT_PENDING,
                 ORDER_PAYMENT_EXPIRED,
                 ORDER_PAYMENT -> BusinessEventType.ORDER_PAYMENT;

            case SUBSCRIPTION_CREATED,
                 SUBSCRIPTION_UPDATED,
                 SUBSCRIPTION_DELETED,
                 SUBSCRIPTION_RENEWED,
                 SUBSCRIPTION_PAYMENT_FAILED,
                 SUBSCRIPTION_TRIAL_ENDING,
                 SUBSCRIPTION_PAUSED,
                 SUBSCRIPTION_RESUMED,
                 CUSTOMER_SUBSCRIPTION_TRIAL_WILL_END,
                 SUBSCRIPTION -> BusinessEventType.SUBSCRIPTION;

            case PAYMENT_ACTION_REQUIRED,
                 INVOICE_PAID,
                 INVOICE_CREATED,
                 INVOICE_UPDATED,
                 INVOICE_FINALIZED,
                 INVOICE_FINALIZATION_FAILED,
                 INVOICE_PAYMENT_ACTION_REQUIRED,
                 INVOICE_PAYMENT_FAILED,
                 INVOICE_PAYMENT_SUCCEEDED,
                 INVOICE -> BusinessEventType.INVOICE;

            default -> BusinessEventType.UNKNOWN_EVENT;
        };
    }
}
