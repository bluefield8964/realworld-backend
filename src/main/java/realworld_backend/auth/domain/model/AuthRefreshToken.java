package realworld_backend.auth.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Entity
@Table(name = "auth_refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Which session this token belongs to
     */
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    /**
     * SHA-256 hash of refresh token (never store raw token)
     */
    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    /**
     * Token family ID (used for rotation + revoke all)
     */
    @Column(name = "family_id", nullable = false, length = 64)
    private String familyId;

    /**
     * Parent token (for rotation chain)
     */
    @Column(name = "parent_id")
    private Long parentId;

    /**
     * Creation time (for audit/debug)
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * When token was used (for replay detection)
     */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /**
     * When token was revoked
     */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * Expiration time
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

}
