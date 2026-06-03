package realworld_backend.auth.infrastructure.stub;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import realworld_backend.auth.domain.model.AuthSession;
import realworld_backend.auth.domain.model.TokenPair;
import realworld_backend.auth.domain.model.UserAuthProfile;
import realworld_backend.auth.domain.service.AuthRefreshTokenService;
import realworld_backend.auth.domain.service.TokenService;
import realworld_backend.auth.security.TokenTool;

import java.time.LocalDateTime;

@Slf4j
@Component
@AllArgsConstructor
public class JwtTokenService implements TokenService {
    private final TokenTool tokenTool;
    private final AuthRefreshTokenService authRefreshTokenService;

    @Override
    public TokenPair issueTokenPair(UserAuthProfile user, AuthSession session, boolean rememberMe) {


        //lasted sessionId
        String sessionId = session.getSessionId();
        //access token already insert into redis
        String accessToken = tokenTool.generateAuthToken(user, sessionId);
        String refreshToken = authRefreshTokenService.generateRefreshToken(user.getId(), session.getDeviceId(), sessionId);
        LocalDateTime localDateTime = UtcTimeMapper.nowUtc().plusHours(1);
        return new TokenPair(accessToken, refreshToken, localDateTime);
    }

    @Override
    public TokenPair issueRefreshedTokenPair(String sessionId, String refreshToken, UserAuthProfile user) {

        String accessToken = tokenTool.generateAuthToken(user, sessionId);
        LocalDateTime localDateTime = UtcTimeMapper.nowUtc().plusHours(1);
        return new TokenPair(accessToken, refreshToken, localDateTime);
    }

}

