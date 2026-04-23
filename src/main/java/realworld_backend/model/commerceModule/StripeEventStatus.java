package realworld_backend.model.commerceModule;

public enum StripeEventStatus {
    PROCESSING,
    SUCCEEDED,
    FAILED,
    DEAD//RETRY_EXHAUSTED
}