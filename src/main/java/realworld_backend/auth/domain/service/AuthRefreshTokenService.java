package realworld_backend.auth.domain.service;

import realworld_backend.auth.domain.model.AuthRefreshToken;
import realworld_backend.auth.domain.model.AuthSession;
import realworld_backend.auth.domain.model.UserAuthProfile;

public interface AuthRefreshTokenService {
    String generateRefreshToken(Long userId,String deviceId, String sessionId);

    void revokeBySessionId(String sessionId);

    AuthRefreshToken findByRefreshToken(String refreshToken);

    String rotationRefreshToken(AuthRefreshToken byRefreshToken);
}
