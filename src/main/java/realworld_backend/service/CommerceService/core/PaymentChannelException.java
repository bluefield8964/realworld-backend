package realworld_backend.service.CommerceService.core;

import com.stripe.exception.StripeException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Data
@Slf4j
public class PaymentChannelException extends RuntimeException {
    private final String provider;      // stripe/paypal
    private final String errorCode;  // card_declined / INSTRUMENT_DECLINED
    private final String requestId;     // provider request id
    private final boolean retryable;    // channel-level hint

    public PaymentChannelException(
            String provider,
            String providerCode,
            String requestId,
            boolean retryable,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.provider = provider;
        this.errorCode = providerCode;
        this.requestId = requestId;
        this.retryable = retryable;
    }




}
