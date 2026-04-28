package realworld_backend.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import realworld_backend.auth.domain.enumerous.SessionStatus;
import realworld_backend.auth.domain.model.AuthSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findByUserIdAndDeviceId(Long userId, String deviceId);

    Optional<AuthSession> findBySessionIdAndStatusAndRevokedAtIsNull(
            String sessionId,
            SessionStatus status
    );

    Optional<AuthSession> findBySessionIdAndStatusAndRevokedAtIsNullAndExpiresAtAfter(
            String sessionId,
            SessionStatus status,
            LocalDateTime now
    );

    Optional<List<AuthSession>> findByUserId(Long userId);

    Optional<List<AuthSession>> findAllByUserIdAndStatus(Long userId, SessionStatus sessionStatus);
}
