package realworld_backend.commerce.model.entitlement;

import jakarta.persistence.*;
import lombok.*;
import realworld_backend.commerce.model.entitlement.enums.EntitlementResourceType;
import realworld_backend.commerce.model.entitlement.enums.EntitlementSourceType;
import realworld_backend.commerce.model.entitlement.enums.EntitlementStatus;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "entitlement_grants",
        indexes = {
                @Index(name = "idx_entitlement_user_resource_status", columnList = "user_id,resource_type,resource_id,status"),
                @Index(name = "idx_entitlement_source", columnList = "source_type,source_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_entitlement_source_resource",
                        columnNames = {"source_type", "source_id", "resource_type", "resource_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntitlementGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, columnDefinition = "VARCHAR(50)")
    private EntitlementResourceType resourceType;

    @Column(name = "resource_id", nullable = false, length = 120)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, columnDefinition = "VARCHAR(50)")
    private EntitlementSourceType sourceType;

    @Column(name = "source_id", nullable = false, length = 120)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private EntitlementStatus status;

    private LocalDateTime effectiveAt;

    private LocalDateTime expireAt;

    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
