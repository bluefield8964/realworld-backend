package realworld_backend.commerce.service.core;

import realworld_backend.commerce.model.Order;
import realworld_backend.commerce.model.core.CheckoutSessionData;
import realworld_backend.commerce.model.core.ProviderEvent;
import realworld_backend.commerce.model.core.ProviderSession;

public interface PaymentChannel {
    String provider(); // "stripe", "paypal"

    CheckoutSessionData createCheckoutSession(Order order) throws PaymentChannelException;

    ProviderEvent parseWebhook(String payload, String sigHeader, String secret) throws PaymentChannelException;

    ProviderSession retrieveSession(String sessionId) throws PaymentChannelException;
}
