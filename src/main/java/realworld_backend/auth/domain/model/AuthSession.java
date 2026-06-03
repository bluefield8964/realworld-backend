package realworld_backend.auth.domain.model;

import realworld_backend.common.time.UtcTimeMapper;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.auth.domain.enumerous.SessionStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_sessions")
@AllArgsConstructor
@NoArgsConstructor(force = true)
@Data
@Builder
public class AuthSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String sessionId;

    private Long userId;
    private String deviceId;
    private String ip;
    private String userAgent;
    private String riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(50)")
    private SessionStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime lastAccessedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;

    public void touch() {
        this.lastAccessedAt = UtcTimeMapper.nowUtc();
        this.expiresAt = this.lastAccessedAt.plusDays(7);
    }

    public void revoke() {
        this.status = SessionStatus.REVOKED;
        this.revokedAt = UtcTimeMapper.nowUtc();
    }

    public boolean isActive() {
        return this.status == SessionStatus.ACTIVE
                && this.revokedAt == null
                && this.expiresAt != null
                && this.expiresAt.isAfter(UtcTimeMapper.nowUtc());
    }
    public void used() {
        this.status = SessionStatus.USED;
        this.revokedAt = UtcTimeMapper.nowUtc();
    }

}

