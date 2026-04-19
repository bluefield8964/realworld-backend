package realworld_backend.model.commerceModule;

public enum PaymentStatus {
    INIT,           // just created
    PROCESSING, // sent to provider
    PAYING,
    SUCCESS,
    FAILED
}
