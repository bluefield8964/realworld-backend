package realworld_backend.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import realworld_backend.auth.domain.enumerous.SessionStatus;
import realworld_backend.auth.domain.model.AuthRefreshToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {
    Optional<AuthRefreshToken> findFirstBySessionIdAndUsedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(
            String sessionId
    );


    @Modifying
    @Query("""
                update AuthRefreshToken t
                set t.revokedAt = :now
                where t.sessionId = :sessionId
                  and t.revokedAt is null
            """)
    void revokeBySessionId(String sessionId, LocalDateTime now);


    @Query("""
                select distinct t.familyId
                from AuthRefreshToken t
                join AuthSession s on t.sessionId = s.sessionId
                where s.userId = :userId
                  and s.deviceId = :deviceId
                  and s.status = :status
                  and s.revokedAt is null
            """)
    List<String> findActiveFamilyIdsByDevice(Long userId, String deviceId, SessionStatus status);


    @Modifying
    @Query("""
                update AuthRefreshToken t
                set t.revokedAt = :now
                where t.familyId in :familyIds
                  and t.revokedAt is null
            """)
    int revokeByFamilyIds(List<String> familyIds, LocalDateTime now);

    Optional<AuthRefreshToken> findByTokenHashAndExpiresAtAfter(String tokenHash, LocalDateTime now);

    @Modifying
    @Query("""
                update AuthRefreshToken t
                set t.usedAt = :now
                where t.id = :id
                  and t.usedAt is null
                  and t.revokedAt is null
                  and t.expiresAt > :now
            """)
    int consumeIfActive(Long id, LocalDateTime now);
}
