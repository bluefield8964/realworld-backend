package realworld_backend.model;

public enum OrderStatus {
    CREATED,        // order created
    PENDING,        // waiting for payment
    PAID,           // payment success
    FAILED,         // payment failed
    CANCELLED;
}
