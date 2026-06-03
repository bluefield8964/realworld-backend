package realworld_backend.commerce.service.statemachine;

/**
 * Describes whether a concrete domain state is terminal, recoverable, or still in-flight.
 */
public enum StateNature {
    TERMINAL_STATE,
    RECOVERABLE_STATE,
    INTERMEDIATE_STATE
}
