package realworld_backend.commerce.model;

public enum PaymentStatus {
    INIT,           // just created
    PROCESSING, // sent to provider
    PAYING,
    SUCCESS,
    FAILED
}

