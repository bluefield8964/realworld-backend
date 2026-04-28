package realworld_backend.auth.api.response;

import realworld_backend.auth.application.result.LoginResult;
import realworld_backend.auth.domain.model.TokenPair;

import java.time.LocalDateTime;

public class RefreshResponse {
    private String accessToken;
    private LocalDateTime accessTokenExpiresAt;

    public static RefreshResponse from(TokenPair tokenPair) {
        RefreshResponse response = new RefreshResponse();


        if (tokenPair != null) {
            response.accessToken = tokenPair.getAccessToken();
            response.accessTokenExpiresAt = tokenPair.getAccessTokenExpiresAt();
        }
        return response;
    }
}
