package realworld_backend.commerce.model.invoice;

import jakarta.persistence.*;
import lombok.*;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.PaymentStatus;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "subscription_invoices",
        indexes = {
                @Index(name = "idx_invoice_subscription_no", columnList = "subscription_no"),
                @Index(name = "idx_invoice_provider_sub_id", columnList = "provider_subscription_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_invoice_id", columnNames = "invoice_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 80)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private BusinessEventType eventType;

    @Column(nullable = false, length = 80)
    private String invoiceId;

    @Column(nullable = false, length = 120)
    private String subscriptionNo;

    @Column(length = 80)
    private String providerSubscriptionId;

    @Column(length = 80)
    private String providerCustomerId;

    @Column(length = 80)
    private String paymentIntentId;

    private Long amountDue;
    private Long amountPaid;
    private Long amountRemaining;

    @Column(length = 10)
    private String currency;

    @Column(length = 40)
    private String providerInvoiceStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private PaymentStatus paymentStatus;

    private Boolean paid;
    private Integer attemptCount;

    @Column(length = 80)
    private String failureCode;

    @Column(length = 255)
    private String failureMessage;

    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private LocalDateTime nextPaymentAttemptAt;
    private LocalDateTime providerCreatedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
