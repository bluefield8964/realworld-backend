package realworld_backend.auth.api.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.auth.application.result.LoginResult;
import realworld_backend.auth.domain.model.TokenPair;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
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
