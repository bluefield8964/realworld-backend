package realworld_backend.commerce.model.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stripe.model.Subscription;
import lombok.Builder;
import lombok.Data;
import realworld_backend.commerce.service.core.ProviderTimeMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ProviderSubscription {

    @JsonProperty("id")
    private String id;

    @JsonProperty("object")
    private String object;

    @JsonProperty("status")
    private String status;

    @JsonProperty("customer")
    private String customer;

    @JsonProperty("current_period_start")
    private Long currentPeriodStart;
    private transient Instant currentPeriodStartAt;

    @JsonProperty("current_period_end")
    private Long currentPeriodEnd;
    private transient Instant currentPeriodEndAt;

    @JsonProperty("cancel_at_period_end")
    private Boolean cancelAtPeriodEnd;

    @JsonProperty("canceled_at")
    private Long canceledAt;
    private transient Instant canceledAtInstant;

    @JsonProperty("latest_invoice")
    private String latestInvoice;

    @JsonProperty("metadata")
    private Map<String, String> metadata;

    @JsonProperty("created")
    private Long created;
    private transient Instant createdAt;

    public static ProviderSubscription mapToProviderSubscription(Subscription subscription) {
        return ProviderSubscription.builder()
                .id(subscription.getId())
                .object(subscription.getObject())
                .status(subscription.getStatus())
                .customer(subscription.getCustomer())
                .currentPeriodStart(subscription.getCurrentPeriodStart())
                .currentPeriodEnd(subscription.getCurrentPeriodEnd())
                .cancelAtPeriodEnd(subscription.getCancelAtPeriodEnd())
                .canceledAt(subscription.getCanceledAt())
                .latestInvoice(subscription.getLatestInvoice())
                .metadata(subscription.getMetadata())
                .created(subscription.getCreated())
                .build();
    }

    public Instant currentPeriodStartInstant() {
        if (currentPeriodStartAt == null) {
            currentPeriodStartAt = ProviderTimeMapper.toInstant(currentPeriodStart);
        }
        return currentPeriodStartAt;
    }

    public LocalDateTime currentPeriodStartUtc() {
        return ProviderTimeMapper.toUtcLocalDateTime(currentPeriodStartInstant());
    }

    public Instant currentPeriodEndInstant() {
        if (currentPeriodEndAt == null) {
            currentPeriodEndAt = ProviderTimeMapper.toInstant(currentPeriodEnd);
        }
        return currentPeriodEndAt;
    }

    public LocalDateTime currentPeriodEndUtc() {
        return ProviderTimeMapper.toUtcLocalDateTime(currentPeriodEndInstant());
    }

    public Instant canceledAtInstant() {
        if (canceledAtInstant == null) {
            canceledAtInstant = ProviderTimeMapper.toInstant(canceledAt);
        }
        return canceledAtInstant;
    }

    public LocalDateTime canceledAtUtc() {
        return ProviderTimeMapper.toUtcLocalDateTime(canceledAtInstant());
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
