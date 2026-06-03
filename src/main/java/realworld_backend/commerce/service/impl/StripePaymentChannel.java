package realworld_backend.commerce.service.impl;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import realworld_backend.auth.domain.model.UserAuthProfile;
import realworld_backend.commerce.model.Order;
import realworld_backend.commerce.model.core.CheckoutSessionData;
import realworld_backend.commerce.model.core.ProviderEvent;
import realworld_backend.commerce.model.core.ProviderInvoice;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.model.core.ProviderSession;
import realworld_backend.commerce.model.core.ProviderSubscription;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.PlanProviderMapping;
import realworld_backend.commerce.model.subscription.SubscriptionPlan;
import realworld_backend.commerce.service.PaymentErrorLogService;
import realworld_backend.commerce.service.subscription.PlanProviderMappingService;
import realworld_backend.commerce.service.core.PaymentChannel;
import realworld_backend.commerce.service.core.PaymentChannelException;

import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class StripePaymentChannel implements PaymentChannel {

    /*
     * ID reference guide:
     * - requestId: provider API request id for support/debug
     * - event.getId(): webhook-level idempotency id
     * - event.data.object.id: business object id (session/payment/subscription/invoice)
     */

    private final PaymentErrorLogService paymentErrorLogService;
    private final PlanProviderMappingService planProviderMappingService;
    @Value("${stripe.checkout.order.success-url}")
    private String orderSuccessUrl;
    @Value("${stripe.checkout.order.cancel-url}")
    private String orderCancelUrl;
    @Value("${stripe.checkout.subscription.success-url}")
    private String subscriptionSuccessUrl;
    @Value("${stripe.checkout.subscription.cancel-url}")
    private String subscriptionCancelUrl;
    // Stripe Billing hard declines: automatic retries should not execute unless payment method changes.
    private static final Set<String> HARD_DECLINE_CODES = Set.of(
            "incorrect_number",
            "lost_card",
            "pickup_card",
            "stolen_card",
            "revocation_of_authorization",
            "revocation_of_all_authorizations",
            "authentication_required",
            "highest_risk_level",
            "transaction_not_allowed"
    );
    // Stripe declines with explicit "attempt again" guidance.
    private static final Set<String> RETRYABLE_DECLINE_CODES = Set.of(
            "processing_error",
            "issuer_not_available",
            "approve_with_id",
            "try_again_later"
    );

    @Override
    public String provider() {
        return "STRIPE";
    }

    @Override
    public CheckoutSessionData createCheckoutSession(Order order) throws PaymentChannelException {
        try {
            SessionCreateParams params =
                    SessionCreateParams.builder()
                            .setMode(SessionCreateParams.Mode.PAYMENT)
                            .setSuccessUrl(orderSuccessUrl)
                            .setCancelUrl(orderCancelUrl)
                            .putMetadata("orderNo", order.getOrderNo())
                            .putMetadata("userId", order.getUserId().toString())
                            .putMetadata("product", String.valueOf(order.getProductId()))
                            .addLineItem(
                                    SessionCreateParams.LineItem.builder()
                                            .setQuantity(1L)
                                            .setPriceData(
                                                    SessionCreateParams.LineItem.PriceData.builder()
                                                            .setCurrency("usd")
                                                            .setUnitAmount(order.getAmount())
                                                            .setProductData(
                                                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                            .setName("VIP Membership")
                                                                            .build()
                                                            ).build()
                                            ).build()
                            ).build();

            // Stripe-side idempotency for network retries.
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(order.getOrderNo())
                    .build();

            return CheckoutSessionData.generateCheckoutSessionData(Session.create(params, options));
        } catch (StripeException e) {
            throw stripeExceptionHandle(e);
        }
    }

    @Override
    public CheckoutSessionData createSubscriptionSession(CustomerSubscription customerSubscription) throws PaymentChannelException {
        try {
            PlanProviderMapping mappingByPlanAndProvider =
                    planProviderMappingService.findMappingByPlanAndProvider(
                            customerSubscription.getPlan(),
                            customerSubscription.getProvider()
                    );
            SubscriptionPlan plan = customerSubscription.getPlan();
            UserAuthProfile user = customerSubscription.getUser();

            // Subscription-mode Checkout Session parameters.
            SessionCreateParams params = SessionCreateParams.builder()
                    // Core mode and redirect URLs.
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl(subscriptionSuccessUrl)
                    .setCancelUrl(subscriptionCancelUrl)
                    // Recurring price reference from provider mapping.
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setPrice(mappingByPlanAndProvider.getProviderPriceId())
                                    .setQuantity(1L)
                                    .build()
                    )
                    // Optional trial configuration.
                    .setSubscriptionData(
                            SessionCreateParams.SubscriptionData.builder()
                                    .setTrialPeriodDays(7L)
                                    .putMetadata("subscription_type", plan.getName())
                                    // These keys must exist on Subscription object metadata
                                    // for customer.subscription.* handlers.
                                    .putMetadata("subscriptionNo", customerSubscription.getSubscriptionNo())
                                    .putMetadata("userId", user.getId().toString())
                                    .putMetadata("planCode", plan.getPlanCode())
                                    .putMetadata("orderNo", customerSubscription.getSubscriptionNo())
                                    .putMetadata("product", String.valueOf(plan.getDescription()))
                                    .build()
                    )
                    // Business correlation fields.
                    .setClientReferenceId(customerSubscription.getSubscriptionNo())
                    .putMetadata("userId", user.getId().toString())
                    .putMetadata("planCode", plan.getPlanCode())
                    // Keep orderNo for compatibility with current webhook/order flow.
                    .putMetadata("orderNo", customerSubscription.getSubscriptionNo())
                    .putMetadata("subscriptionNo", customerSubscription.getSubscriptionNo())
                    .putMetadata("product", String.valueOf(plan.getDescription()))
                    // Customer identity.
                    .setCustomerEmail(user.getEmail())
                    // Allowed payment method types.
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    // Allow Stripe promotion codes.
                    .setAllowPromotionCodes(true)
                    .build();

            // Stripe-side idempotency for network retries.
            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(customerSubscription.getSubscriptionNo())
                    .build();

            return CheckoutSessionData.generateSubscriptionSessionData(Session.create(params, options));
        } catch (StripeException e) {
            throw stripeExceptionHandle(e);
        }
    }

    @Override
    public ProviderEvent parseWebhook(String payload, String sigHeader, String secret) throws PaymentChannelException {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, secret);
            return ProviderEvent.fromProviderEvent(event, "STRIPE");
        } catch (StripeException e) {
            throw stripeExceptionHandle(e);
        }
    }

    @Override
    public ProviderRawEvent parseRawEvent(String payload, String sigHeader, String secret) throws PaymentChannelException {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, secret);
            return ProviderRawEvent.fromProviderEvent(event, "STRIPE");
        } catch (StripeException e) {
            throw stripeExceptionHandle(e);
        }
    }

    @Override
    public ProviderSession retrieveSession(String sessionId) throws PaymentChannelException {
        try {
            Session retrieve = Session.retrieve(sessionId);
            return ProviderSession.mapToProviderSession(retrieve);
        } catch (StripeException e) {
            throw stripeExceptionHandle(e);
        }
    }

    @Override
    public ProviderSubscription retrieveSubscription(String subscriptionId) throws PaymentChannelException {
        try {
            Subscription retrieve = Subscription.retrieve(subscriptionId);
            return ProviderSubscription.mapToProviderSubscription(retrieve);
        } catch (StripeException e) {
            throw stripeExceptionHandle(e);
        }
    }

    @Override
    public ProviderInvoice retrieveInvoice(String invoiceId) throws PaymentChannelException {
        try {
            Invoice retrieve = Invoice.retrieve(invoiceId);
            return ProviderInvoice.mapToProviderInvoice(retrieve);
        } catch (StripeException e) {
            throw stripeExceptionHandle(e);
        }
    }

    private PaymentChannelException stripeExceptionHandle(StripeException e) {
        String requestId = e.getRequestId();
        String errorMessage = e.getMessage();
        String errorCode = resolveProviderErrorCode(e);
        boolean retryable = isRetryableStripeException(e, errorCode);

        // Log for debugging.
        log.error("Stripe API Error - Request ID: {}, Code: {}, Message: {}",
                requestId, errorCode, errorMessage);
        // Persist for support/debug correlation.
        paymentErrorLogService.saveErrorForSupport(requestId, errorCode, errorMessage, "STRIPE");

        return new PaymentChannelException(
                "STRIPE",
                errorCode,
                e.getRequestId(),
                retryable,
                e.getMessage(),
                e
        );
    }

    String resolveProviderErrorCode(StripeException e) {
        if (e instanceof CardException cardException) {
            String declineCode = normalizeCode(cardException.getDeclineCode());
            if (declineCode != null) {
                return declineCode;
            }
        }
        String code = normalizeCode(e.getCode());
        if (code != null) {
            return code;
        }
        Integer statusCode = e.getStatusCode();
        return statusCode == null ? "unknown_error" : "http_" + statusCode;
    }

    boolean isRetryableStripeException(StripeException e, String normalizedCode) {
        // Transport/infrastructure issues: retry with backoff.
        if (e instanceof ApiConnectionException || e instanceof ApiException || e instanceof RateLimitException) {
            return true;
        }

        // Credential/signature/request-shape/idempotency issues: do not retry blindly.
        if (e instanceof SignatureVerificationException
                || e instanceof AuthenticationException
                || e instanceof InvalidRequestException
                || e instanceof IdempotencyException) {
            return false;
        }

        if (e instanceof CardException) {
            if (normalizedCode == null) {
                return false;
            }
            if (HARD_DECLINE_CODES.contains(normalizedCode)) {
                return false;
            }
            if (RETRYABLE_DECLINE_CODES.contains(normalizedCode)) {
                return true;
            }
            // Most remaining card declines require user action (new card/issuer contact/auth).
            return false;
        }

        // Fallback by HTTP status when subtype does not provide a clear policy.
        Integer statusCode = e.getStatusCode();
        return statusCode != null && (statusCode == 429 || statusCode >= 500);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toLowerCase(Locale.ROOT);
    }
}

