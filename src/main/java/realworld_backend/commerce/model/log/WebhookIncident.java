package realworld_backend.commerce.model.log;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Entity
@Table(
        name = "webhook_incidents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_webhook_incident_dedupe_key",
                        columnNames = {"dedupe_key"}
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookIncident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain_type", columnDefinition = "VARCHAR(50)")
    private AbnormalDomainType domainType;

    private String eventId;
    private String eventType;

    private String requestId;

    // Business-side correlation key, if available.
    private String trackingId;

    // Provider-side object or request correlation key, if available.
    private String providerTrackingId;

    @Column(name = "dedupe_key", nullable = false, unique = true, length = 200)
    private String dedupeKey;

    private String payloadDigest;

    @Lob
    private String rawPayload;

    private String reason;
    private String errorMessage;

    private int occurrenceCount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", columnDefinition = "VARCHAR(50)")
    private WebhookIncidentType incidentType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(50)")
    private WebhookIncidentStatus status;

    private LocalDateTime firstOccurredAt;
    private LocalDateTime lastOccurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime handledAt;
    private String remark;
}
