package realworld_backend.commerce.model.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stripe.model.Invoice;
import lombok.Builder;
import lombok.Data;
import realworld_backend.commerce.service.core.ProviderTimeMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ProviderInvoice {

    @JsonProperty("id")
    private String id;

    @JsonProperty("object")
    private String object;

    @JsonProperty("status")
    private String status;

    @JsonProperty("paid")
    private Boolean paid;

    @JsonProperty("customer")
    private String customer;

    @JsonProperty("subscription")
    private String subscription;

    @JsonProperty("payment_intent")
    private String paymentIntent;

    @JsonProperty("amount_due")
    private Long amountDue;

    @JsonProperty("amount_paid")
    private Long amountPaid;

    @JsonProperty("amount_remaining")
    private Long amountRemaining;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("attempt_count")
    private Long attemptCount;

    @JsonProperty("next_payment_attempt")
    private Long nextPaymentAttempt;
    private transient Instant nextPaymentAttemptAt;

    @JsonProperty("period_start")
    private Long periodStart;
    private transient Instant periodStartAt;

    @JsonProperty("period_end")
    private Long periodEnd;
    private transient Instant periodEndAt;

    @JsonProperty("metadata")
    private Map<String, String> metadata;

    @JsonProperty("created")
    private Long created;
    private transient Instant createdAt;

    public static ProviderInvoice mapToProviderInvoice(Invoice invoice) {
        return ProviderInvoice.builder()
                .id(invoice.getId())
                .object(invoice.getObject())
                .status(invoice.getStatus())
                .paid(invoice.getPaid())
                .customer(invoice.getCustomer())
                .subscription(invoice.getSubscription())
                .paymentIntent(invoice.getPaymentIntent())
                .amountDue(invoice.getAmountDue())
                .amountPaid(invoice.getAmountPaid())
                .amountRemaining(invoice.getAmountRemaining())
                .currency(invoice.getCurrency())
                .attemptCount(invoice.getAttemptCount())
                .nextPaymentAttempt(invoice.getNextPaymentAttempt())
                .periodStart(invoice.getPeriodStart())
                .periodEnd(invoice.getPeriodEnd())
                .metadata(invoice.getMetadata())
                .created(invoice.getCreated())
                .build();
    }

    public boolean isPaidSuccessfully() {
        return "paid".equals(status) && Boolean.TRUE.equals(paid);
    }

    public Instant periodStartInstant() {
        if (periodStartAt == null) {
            periodStartAt = ProviderTimeMapper.toInstant(periodStart);
        }
        return periodStartAt;
    }

    public LocalDateTime periodStartUtc() {
        return ProviderTimeMapper.toUtcLocalDateTime(periodStartInstant());
    }

    public Instant periodEndInstant() {
        if (periodEndAt == null) {
            periodEndAt = ProviderTimeMapper.toInstant(periodEnd);
        }
        return periodEndAt;
    }

    public LocalDateTime periodEndUtc() {
        return ProviderTimeMapper.toUtcLocalDateTime(periodEndInstant());
    }

    public Instant nextPaymentAttemptInstant() {
        if (nextPaymentAttemptAt == null) {
            nextPaymentAttemptAt = ProviderTimeMapper.toInstant(nextPaymentAttempt);
        }
        return nextPaymentAttemptAt;
    }

    public LocalDateTime nextPaymentAttemptUtc() {
        return ProviderTimeMapper.toUtcLocalDateTime(nextPaymentAttemptInstant());
    }

    public Instant createdAtInstant() {
        if (createdAt == null) {
            createdAt = ProviderTimeMapper.toInstant(created);
        }
        return createdAt;
    }

    public LocalDateTime createdAtUtc() {
        return ProviderTimeMapper.toUtcLocalDateTime(createdAtInstant());
    }
}
