package realworld_backend.auth.application.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import realworld_backend.auth.domain.model.TokenPair;

@Data
@AllArgsConstructor
public class LoginResult {
    private final boolean success;
    private final boolean mfaRequired;
    private final String challengeId;
    private final TokenPair tokenPair;
    private final String sessionId;

    public static LoginResult success(TokenPair tokenPair, String sessionId) {
        return new LoginResult(true, false, null, tokenPair, sessionId);
    }

    public static LoginResult mfaRequired(String challengeId) {
        return new LoginResult(false, true, challengeId, null, null);
    }

}
