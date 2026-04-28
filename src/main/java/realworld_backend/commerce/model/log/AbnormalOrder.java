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
        name = "abnormal_orders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_abnormal_domain_order_no",
                        columnNames = {"domain_type", "order_no"}
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AbnormalOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String provider;
    @Enumerated(EnumType.STRING)
    @Column(name = "domain_type", columnDefinition = "VARCHAR(50)")
    private AbnormalDomainType domainType;
    // Business tracking key:
    // - order domain: orderNo
    // - subscription domain: subscriptionNo
    // - invoice domain: business subscriptionNo/orderNo used for local reconciliation, never provider invoice id
    private String orderNo;
    private String eventId;
    private String eventType;

    @Column(unique = true)
    // Provider object tracking id:
    // - order domain: checkout session id (cs_xxx)
    // - invoice domain: invoice id (in_xxx)
    // - subscription domain: legacy fallback may store provider subscription id (sub_xxx)
    private String sessionId;

    @Column(unique = true)
    private String requestId;
    private String rawPayload;
    private String reason;
    private String errorMessage;
    private int retryCount;
    @Enumerated(EnumType.STRING)
    @Column(name = "abnormal_type", columnDefinition = "VARCHAR(50)")
    private AbnormalOrderType abnormalType;
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(50)")
    private AbnormalOrderStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private LocalDateTime lastRetryAt;
    private LocalDateTime nextRetryAt;
    private LocalDateTime handledAt;
    private String remark;


}

