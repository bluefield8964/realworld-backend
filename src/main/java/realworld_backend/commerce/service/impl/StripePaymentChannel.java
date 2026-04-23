package realworld_backend.commerce.service.impl;

import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.model.Order;
import realworld_backend.commerce.model.core.CheckoutSessionData;
import realworld_backend.commerce.model.core.ProviderEvent;
import realworld_backend.commerce.model.core.ProviderSession;
import realworld_backend.commerce.service.core.PaymentChannel;
import realworld_backend.commerce.service.core.PaymentChannelException;
import realworld_backend.commerce.service.PaymentErrorLogService;

@Slf4j
@Component
@RequiredArgsConstructor
public class StripePaymentChannel implements PaymentChannel {

    /*
     *
     *  ID Type	                            Format               Purpose	                                        Example
     *  requestId	req_xxxxx	                req_xxxxx            Tracks API request for debugging                   req_abc123def456
     *  event.getId()	evt_xxxxx	            evt_xxxxx            Identifies webhook event	                        evt_xyz789ghi012
     *  event.getData().getObject().getId()	cs_xxxxx or pi_xxxxx Identifies business object (Session/PaymentIntent)	cs_test_session123
     *
     *  requestId: Debug API calls, contact Stripe support
     *  event.getId(): Prevent duplicate webhook processing
     *  event.getData().getObject().getId(): Your business logic (session/payment ID)
     *
     * */

    private final PaymentErrorLogService paymentErrorLogService;

    @Override
    public String provider() {
        return "stripe";
    }

    @Override
    public CheckoutSessionData createCheckoutSession(Order order) throws PaymentChannelException {
        try {
            SessionCreateParams params =
                    SessionCreateParams.builder()
                            .setMode(SessionCreateParams.Mode.PAYMENT)
                            .setSuccessUrl("http://localhost:3000/success")
                            .setCancelUrl("http://localhost:3000/cancel")
                            .putMetadata("orderNo", order.getOrderNo())
                            .putMetadata("userId", order.getUserId().toString())
                            .putMetadata("internalCustomerId", order.getUserId().toString())
                            .putMetadata("product", String.valueOf(order.getProductId()))
                            .addLineItem(
                                    SessionCreateParams.LineItem.builder()
                                            .setQuantity(1L)
                                            .setPriceData(//money
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
                    .setIdempotencyKey(order.getOrderNo()) //make sure every single Order can only generate one StripeRequestOrder
                    .build();
            return CheckoutSessionData.generateStripeCheckoutSessionData(Session.create(params, options));
        } catch (StripeException e) {
            throw stripeExceptionHandle(e);
        }
    }

    @Override
    public ProviderEvent parseWebhook(String payload, String sigHeader, String secret) throws PaymentChannelException {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, secret);
            return ProviderEvent.fromProviderEvent(event, "stripe");
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

    private PaymentChannelException stripeExceptionHandle(StripeException e) {
        String requestId = e.getRequestId();
        String errorMessage = e.getMessage();
        String errorCode = e.getCode();
        // Log for debugging
        log.error("Stripe API Error - Request ID: {}, Code: {}, Message: {}",
                requestId, errorCode, errorMessage);
        // Store for support tickets
        paymentErrorLogService.saveErrorForSupport(requestId, errorCode, errorMessage, "stripe");
        return new PaymentChannelException(
                "stripe",       //provider
                e.getCode(),    // card_declined / INSTRUMENT_DECLINED
                e.getRequestId(),//
                true, // or by code mapping
                e.getMessage(),
                e
        );
    }
}

