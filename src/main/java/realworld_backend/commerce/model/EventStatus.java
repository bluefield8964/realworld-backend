package realworld_backend.commerce.model;

public enum EventStatus {
    PROCESSING,
    SUCCEEDED,
    FAILED,
    DEAD//RETRY_EXHAUSTED
}
