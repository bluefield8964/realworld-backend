package realworld_backend.commerce.service.statemachine;

/**
 * Transition classification used by webhook state machines.
 */
public enum TransitionClass {
    LEGAL_TRANSITION,
    RETRYABLE_ILLEGAL_TRANSITION,
    ILLEGAL_TRANSITION,
    IGNORE_TRANSITION
}
