package realworld_backend.commerce.model.subscription;


import jakarta.persistence.*;
import lombok.*;
import realworld_backend.commerce.model.PaymentStatus;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "subscription_historys",
        indexes = {
                @Index(name = "idx_sub_history_sub_created", columnList = "subscription_id,createdAt")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private CustomerSubscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private SubscriptionStatus action;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(50)")
    private SubscriptionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(50)")
    private SubscriptionStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(50)")
    private PaymentStatus paymentStatus;

    @Column(length = 255)
    private String reason;

    // Usually webhook event time or decision effective time
    private LocalDateTime effectiveDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

