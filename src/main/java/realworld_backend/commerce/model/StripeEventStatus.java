package realworld_backend.commerce.model;

public enum StripeEventStatus {
    PROCESSING,
    SUCCEEDED,
    FAILED,
    DEAD//RETRY_EXHAUSTED
}
