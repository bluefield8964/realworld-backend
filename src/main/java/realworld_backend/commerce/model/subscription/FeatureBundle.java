package realworld_backend.commerce.model.subscription;

import jakarta.persistence.*;
import lombok.*;
import realworld_backend.commerce.model.subscription.enums.FeatureBundleStatus;
import realworld_backend.commerce.model.subscription.enums.FeatureCode;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "feature_bundles",
        indexes = {
                @Index(name = "idx_feature_bundle_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeatureBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String bundleCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private FeatureBundleStatus status;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "feature_bundle_items",
            joinColumns = @JoinColumn(name = "bundle_id", nullable = false)
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "feature_code", nullable = false, columnDefinition = "VARCHAR(50)")
    @Builder.Default
    private Set<FeatureCode> features = new LinkedHashSet<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
