package realworld_backend.auth.infrastructure.stub;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import realworld_backend.auth.domain.enumerous.SessionStatus;
import realworld_backend.auth.domain.exception.AuthException;
import realworld_backend.auth.domain.exception.UserAuthException;
import realworld_backend.auth.domain.model.AuthSession;
import realworld_backend.auth.domain.model.LoginContext;
import realworld_backend.auth.domain.model.UserAuthProfile;
import realworld_backend.auth.domain.service.RiskDecision;
import realworld_backend.auth.domain.service.SessionService;
import realworld_backend.auth.repository.AuthSessionRepository;
import realworld_backend.common.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@AllArgsConstructor
public class InMemorySessionService implements SessionService {
    private final AuthSessionRepository authSessionRepository;

    @Override
    public AuthSession createSession(UserAuthProfile user, LoginContext loginContext, RiskDecision riskDecision) {
        Optional<AuthSession> authSessionByIdAndDeviceId = authSessionRepository.findByUserIdAndDeviceId(loginContext.getUserId(), loginContext.getDeviceId());
        LocalDateTime now = LocalDateTime.now();
        AuthSession  authSession = AuthSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .userId(user.getId())
                .deviceId(loginContext.getDeviceId())
                .ip(loginContext.getIp())
                .userAgent(loginContext.getUserAgent())
                .riskLevel(riskDecision.getLevel())
                .status(SessionStatus.ACTIVE)
                .createdAt(now)
                .expiresAt(now.plusDays(7))
                .revokedAt(null)
                .lastAccessedAt(now)
                .build();
        AuthSession elderSession;

        //extreme situation
        if (authSessionByIdAndDeviceId.isPresent()) {
            elderSession = authSessionByIdAndDeviceId.get();
            elderSession.used();
            authSessionRepository.save(elderSession);
        }
        authSessionRepository.save(authSession);
        return authSession;

    }
    // reserved for future revoke-all
    @Override
    public List<AuthSession> findByUserId(Long userId) {
        Optional<List<AuthSession>> byId = authSessionRepository.findAllByUserIdAndStatus(userId,SessionStatus.ACTIVE);
        return byId.orElse(null);
    }

    @Override
    public AuthSession findByUserIdAndDeviceId(Long userId, String deviceId) {
        Optional<AuthSession> byId = authSessionRepository.findByUserIdAndDeviceId(userId, deviceId);
        return byId.orElse(null);
    }

    @Override
    public AuthSession revokeBySessionId(Long userId, String sessionId) {
        Optional<AuthSession> AuthSession = authSessionRepository.findBySessionIdAndStatusAndRevokedAtIsNull(sessionId, SessionStatus.ACTIVE);
        if (AuthSession.isPresent() && AuthSession.get().getStatus() == SessionStatus.ACTIVE) {
            AuthSession authSession = AuthSession.get();
            if (!authSession.getUserId().equals(userId)) {
                throw new UserAuthException(ErrorCode.FORBIDDEN);
            }
            authSession.used();
            authSessionRepository.save(authSession);
            return authSession;
        } else {
            return null;
        }
    }

    @Override
    public List<AuthSession> revokeAllBySessionId(Long aLong, String s) {
        return List.of();
    }

    @Override
    public AuthSession findBySessionId(String sessionId) {
        AuthSession session = authSessionRepository
                .findBySessionIdAndStatusAndRevokedAtIsNull(sessionId, SessionStatus.ACTIVE)
                .orElseThrow(() ->new AuthException(ErrorCode.SESSION_NOT_FOUND));

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthException(ErrorCode.SESSION_EXPIRED);
        }
        return session;
    }


}
