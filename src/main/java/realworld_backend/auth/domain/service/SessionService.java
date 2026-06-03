package realworld_backend.auth.domain.service;

import realworld_backend.auth.domain.model.AuthSession;
import realworld_backend.auth.domain.model.LoginContext;
import realworld_backend.auth.domain.model.UserAuthProfile;

import java.util.List;

public interface SessionService {
    AuthSession createSession(UserAuthProfile user, LoginContext loginContext, RiskDecision riskDecision);

    List<AuthSession> findByUserId(Long userId);

    AuthSession findByUserIdAndDeviceId(Long userId, String deviceId);

    AuthSession revokeBySessionId(Long userId, String sessionId);

    List<AuthSession> revokeAllBySessionId(Long aLong, String s);

    AuthSession findBySessionId(String sessionId);

}
