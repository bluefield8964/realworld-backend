package realworld_backend.commerce.service.core;

import lombok.Data;

@Data
public class PaymentChannelException extends RuntimeException {
    private final String provider;      // STRIPE/PAYPAL
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

