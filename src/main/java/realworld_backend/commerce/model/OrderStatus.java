package realworld_backend.commerce.model;

public enum OrderStatus {
    CREATED,        // order created
    PENDING,        // waiting for payment
    PAID,           // payment success
    FAILED,         // payment failed
    PAYMENT_FAILED_RETRYABLE, //payment failed
    RE_PENDING, //order was fail , but is reconciling
    CANCELLED;
}

