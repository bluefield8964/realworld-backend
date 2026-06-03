package realworld_backend.auth.domain.service;

import realworld_backend.auth.domain.model.AuthSession;
import realworld_backend.auth.domain.model.TokenPair;
import realworld_backend.auth.domain.model.UserAuthProfile;

public interface TokenService {
    TokenPair issueTokenPair(UserAuthProfile user, AuthSession session, boolean rememberMe);

    TokenPair issueRefreshedTokenPair(String sessionId,String refreshToken, UserAuthProfile user);

}
