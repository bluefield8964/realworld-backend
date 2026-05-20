package realworld_backend.commerce.model.subscription;

import jakarta.persistence.*;
import lombok.*;
import realworld_backend.commerce.model.subscription.enums.BillingInterval;
import realworld_backend.commerce.model.subscription.enums.PlanStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "subscription_plans",
        indexes = {
                @Index(name = "idx_plan_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Stable business identifier, e.g. "creator_pro_monthly"
    @Column(nullable = false, unique = true, length = 64)
    private String planCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private Duration duration;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private PlanStatus status;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;


    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private BillingInterval billingInterval;


    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
