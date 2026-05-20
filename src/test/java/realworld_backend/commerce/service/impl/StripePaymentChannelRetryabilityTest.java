package realworld_backend.commerce.service.impl;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import realworld_backend.commerce.service.PaymentErrorLogService;
import realworld_backend.commerce.service.subscription.PlanProviderMappingService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class StripePaymentChannelRetryabilityTest {

    private StripePaymentChannel stripePaymentChannel;

    @BeforeEach
    void setUp() {
        stripePaymentChannel = new StripePaymentChannel(
                mock(PaymentErrorLogService.class),
                mock(PlanProviderMappingService.class)
        );
    }

    @Test
    void shouldMarkApiConnectionErrorAsRetryable() {
        ApiConnectionException ex = new ApiConnectionException("network timeout");
        String code = stripePaymentChannel.resolveProviderErrorCode(ex);

        assertTrue(stripePaymentChannel.isRetryableStripeException(ex, code));
    }

    @Test
    void shouldMarkHardDeclineAsNonRetryable() {
        CardException ex = new CardException(
                "card declined",
                "req_123",
                "card_declined",
                null,
                "authentication_required",
                null,
                402,
                null
        );
        String code = stripePaymentChannel.resolveProviderErrorCode(ex);

        assertEquals("authentication_required", code);
        assertFalse(stripePaymentChannel.isRetryableStripeException(ex, code));
    }

    @Test
    void shouldMarkProcessingErrorAsRetryable() {
        CardException ex = new CardException(
                "processing error",
                "req_456",
                "processing_error",
                null,
                null,
                null,
                402,
                null
        );
        String code = stripePaymentChannel.resolveProviderErrorCode(ex);

        assertEquals("processing_error", code);
        assertTrue(stripePaymentChannel.isRetryableStripeException(ex, code));
    }

    @Test
    void shouldMarkInvalidRequestAsNonRetryable() {
        InvalidRequestException ex = new InvalidRequestException(
                "invalid request",
                "metadata",
                "req_789",
                "parameter_invalid_empty",
                400,
                null
        );
        String code = stripePaymentChannel.resolveProviderErrorCode(ex);

        assertFalse(stripePaymentChannel.isRetryableStripeException(ex, code));
    }
}

