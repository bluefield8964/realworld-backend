package realworld_backend.auth.domain.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.time.LocalDateTime;

@Entity
@Table(name = "auth_audit_logs")
@NoArgsConstructor(force = true)
@Data
public class AuthAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String eventType;

    private String result;

    private String ip;

    private String deviceId;

    private String requestId;

    private String userAgent;

    @Column(columnDefinition = "json")
    private String payloadJson;

    private LocalDateTime createdAt;

    public AuthAuditLog(Long userId, String eventType, String result,
                        String ip, String deviceId,
                        String requestId, String userAgent,
                        String payloadJson, LocalDateTime createdAt) {
        this.userId = userId;
        this.eventType = eventType;
        this.result = result;
        this.ip = ip;
        this.deviceId = deviceId;
        this.requestId = requestId;
        this.userAgent = userAgent;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
    }
}
