package realworld_backend.service.CommerceService.core;

import realworld_backend.model.commerceModule.Order;
import realworld_backend.model.commerceModule.core.CheckoutSessionData;
import realworld_backend.model.commerceModule.core.ProviderEvent;
import realworld_backend.model.commerceModule.core.ProviderSession;

public interface PaymentChannel {
    String provider(); // "stripe", "paypal"

    CheckoutSessionData createCheckoutSession(Order order) throws PaymentChannelException;

    ProviderEvent parseWebhook(String payload, String sigHeader, String secret) throws PaymentChannelException;

    ProviderSession retrieveSession(String sessionId) throws PaymentChannelException;
}