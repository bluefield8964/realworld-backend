package realworld_backend.commerce.service.core;

import realworld_backend.commerce.model.Order;
import realworld_backend.commerce.model.core.CheckoutSessionData;
import realworld_backend.commerce.model.core.ProviderEvent;
import realworld_backend.commerce.model.core.ProviderInvoice;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.model.core.ProviderSession;
import realworld_backend.commerce.model.core.ProviderSubscription;
import realworld_backend.commerce.model.subscription.CustomerSubscription;

public interface PaymentChannel {
    String provider(); // "STRIPE", "PAYPAL"

    CheckoutSessionData createCheckoutSession(Order order) throws PaymentChannelException;

    CheckoutSessionData createSubscriptionSession(CustomerSubscription customerSubscription) throws PaymentChannelException;

    ProviderEvent parseWebhook(String payload, String sigHeader, String secret) throws PaymentChannelException;

    ProviderRawEvent parseRawEvent(String payload, String sigHeader, String secret) throws PaymentChannelException;

    ProviderSession retrieveSession(String sessionId) throws PaymentChannelException;

    ProviderSubscription retrieveSubscription(String subscriptionId) throws PaymentChannelException;

    ProviderInvoice retrieveInvoice(String invoiceId) throws PaymentChannelException;
}

