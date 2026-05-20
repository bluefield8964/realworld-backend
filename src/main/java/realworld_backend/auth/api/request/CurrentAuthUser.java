package realworld_backend.auth.api.request;

/**
 * Lightweight authenticated user view resolved from the current JWT.
 */
public record CurrentAuthUser(Long userId,
                              String sessionId) {
}
