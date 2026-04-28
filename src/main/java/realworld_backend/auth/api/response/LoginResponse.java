package realworld_backend.auth.api.response;

import realworld_backend.auth.application.result.LoginResult;

import java.time.LocalDateTime;

public class LoginResponse {
    private boolean success;
    private boolean mfaRequired;
    private String challengeId;
    private String accessToken;
    private LocalDateTime accessTokenExpiresAt;
    private String sessionId;
    public static LoginResponse from(LoginResult result) {
        LoginResponse response = new LoginResponse();
        response.success = result.isSuccess();
        response.mfaRequired = result.isMfaRequired();
        response.challengeId = result.getChallengeId();
        response.sessionId = result.getSessionId();

        if (result.getTokenPair() != null) {
            response.accessToken = result.getTokenPair().getAccessToken();
            response.accessTokenExpiresAt = result.getTokenPair().getAccessTokenExpiresAt();
        }
        return response;
    }
}
