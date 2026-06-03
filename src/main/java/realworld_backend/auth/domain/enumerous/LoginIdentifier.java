package realworld_backend.auth.domain.enumerous;

public record LoginIdentifier (
        LoginIdentifierType type,
        String value
) {}