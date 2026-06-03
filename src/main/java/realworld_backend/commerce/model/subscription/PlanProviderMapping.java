package realworld_backend.commerce.model.subscription;

import jakarta.persistence.*;
import lombok.*;
import realworld_backend.commerce.model.subscription.enums.ProviderType;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "plan_provider_mappings",
        uniqueConstraints = {
                // one plan has one active mapping per provider + external price id
                @UniqueConstraint(
                        name = "uk_plan_provider_price",
                        columnNames = {"plan_id", "provider", "providerPriceId"}
                )
        },
        indexes = {
                @Index(name = "idx_provider_price", columnList = "provider,providerPriceId"),
                @Index(name = "idx_provider_product", columnList = "provider,providerProductId")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanProviderMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // internal plan
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private ProviderType provider;

    // provider-side identifiers
    @Column(length = 128)
    private String providerProductId;

    @Column(nullable = false, length = 128)
    private String providerPriceId;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
