package realworld_backend.commerce.service.impl.parser;

import org.springframework.stereotype.Component;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.checkoutPayment.CheckoutSessionWebhookEvent;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.service.core.WebhookObjectParser;

@Component

public class CheckoutSessionWebhookParser implements WebhookObjectParser {

    @Override
    public BusinessEventType supports() {
        return BusinessEventType.ORDER_PAYMENT;
    }

    @Override
    public CheckoutSessionWebhookEvent parse(ProviderRawEvent e) {
        return CheckoutSessionWebhookEvent.parseProviderRawEvent(e);
    }
}

