package realworld_backend.commerce.model.subscription;
import jakarta.persistence.*;
import lombok.*;
import realworld_backend.auth.domain.model.UserAuthProfile;
import realworld_backend.commerce.model.subscription.enums.ProviderType;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;

import java.time.LocalDateTime;
@Entity
@Table(
        name = "customer_subscriptions",
        indexes = {
                @Index(name = "idx_sub_user_status", columnList = "user_id,status"),
                @Index(name = "idx_sub_period_end", columnList = "current_period_end")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_provider_subscription_id", columnNames = "provider_subscription_id"),
                @UniqueConstraint(name = "uk_subscription_no", columnNames = "subscription_no")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Use userId to stay consistent with your current project model.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id",nullable = false)
    private UserAuthProfile user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private ProviderType provider;

    @Column(length = 512)
    private String subscriptionUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private SubscriptionStatus status;

    // identifiers  in Subscription event , it named as  SubscriptionId
    @Column( length = 64)
    private String providerSubscriptionId;

    @Column(length = 64)
    private String providerCustomerId;

    // Subscription cycle truth
    private LocalDateTime currentPeriodStart;

    @Column(length = 120, unique = true)
    private String activeKey;

    @Column(nullable = false, length = 120)
    private String subscriptionNo;

    @Column(nullable = false)
    private LocalDateTime currentPeriodEnd;

    // If true, keep ACTIVE until period end and then mark canceled.
    @Column(nullable = false)
    private Boolean cancelAtPeriodEnd;

    private LocalDateTime canceledAt;

    private LocalDateTime lastCheckoutEventCreatedAt;

    private LocalDateTime lastLifecycleEventCreatedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public boolean isActiveNow(LocalDateTime now) {
        return status == SubscriptionStatus.ACTIVE
                && (currentPeriodEnd == null || now.isBefore(currentPeriodEnd));
    }
}
