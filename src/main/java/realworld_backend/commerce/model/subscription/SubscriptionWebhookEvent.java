package realworld_backend.commerce.model.subscription;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.core.ProviderEventInterface;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.service.core.ProviderTimeMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Builder
@Data
public class SubscriptionWebhookEvent implements ProviderEventInterface {
    private static final Gson WEBHOOK_GSON = new GsonBuilder().create();

    @JsonProperty("id")
    private String eventId;

    @JsonProperty("type")
    private BusinessEventType eventType; // customer.subscription.*

    private String provider;

    @JsonProperty("created")
    private Long created;
    private transient Instant createdAt;

    @JsonProperty("object")
    private SubscriptionObject object;

    @Data
    public static class SubscriptionObject {
        @JsonProperty("id")
        private String id; // Subscription ID

        @JsonProperty("object")
        private String object; // subscription

        @JsonProperty("status")
        private String status; // active/trialing/past_due/canceled/unpaid

        @JsonProperty("customer")
        private String customer;

        @JsonProperty("current_period_start")
        @SerializedName("current_period_start")
        private Long currentPeriodStart;

        @JsonProperty("current_period_end")
        @SerializedName("current_period_end")
        private Long currentPeriodEnd;

        @JsonProperty("billing_cycle_anchor")
        @SerializedName("billing_cycle_anchor")
        private Long billingCycleAnchor;

        @JsonProperty("trial_start")
        @SerializedName("trial_start")
        private Long trialStart;

        @JsonProperty("trial_end")
        @SerializedName("trial_end")
        private Long trialEnd;

        @JsonProperty("cancel_at_period_end")
        @SerializedName("cancel_at_period_end")
        private Boolean cancelAtPeriodEnd;

        @JsonProperty("canceled_at")
        @SerializedName("canceled_at")
        private Long canceledAt;

        @JsonProperty("ended_at")
        @SerializedName("ended_at")
        private Long endedAt;

        @JsonProperty("latest_invoice")
        @SerializedName("latest_invoice")
        private String latestInvoice;

        @JsonProperty("default_payment_method")
        @SerializedName("default_payment_method")
        private String defaultPaymentMethod;

        @JsonProperty("collection_method")
        @SerializedName("collection_method")
        private String collectionMethod;

        @JsonProperty("items")
        private SubscriptionItems items;

        @JsonProperty("pause_collection")
        @SerializedName("pause_collection")
        private PauseCollection pauseCollection;

        @JsonProperty("pending_update")
        @SerializedName("pending_update")
        private PendingUpdate pendingUpdate;

        @JsonProperty("metadata")
        private Map<String, String> metadata;

        @JsonProperty("created")
        private Long created;
        private transient Instant currentPeriodStartAt;
        private transient Instant currentPeriodEndAt;
        private transient Instant trialStartAt;
        private transient Instant trialEndAt;
        private transient Instant createdAt;

        public Instant currentPeriodStartInstant() {
            if (currentPeriodStartAt == null) {
                currentPeriodStartAt = ProviderTimeMapper.toInstant(resolveCurrentPeriodStartEpoch());
            }
            return currentPeriodStartAt;
        }

        public LocalDateTime currentPeriodStartUtc() {
            return ProviderTimeMapper.toUtcLocalDateTime(currentPeriodStartInstant());
        }

        public Instant currentPeriodEndInstant() {
            if (currentPeriodEndAt == null) {
                currentPeriodEndAt = ProviderTimeMapper.toInstant(resolveCurrentPeriodEndEpoch());
            }
            return currentPeriodEndAt;
        }

        public LocalDateTime currentPeriodEndUtc() {
            return ProviderTimeMapper.toUtcLocalDateTime(currentPeriodEndInstant());
        }

        public Instant trialStartInstant() {
            if (trialStartAt == null) {
                trialStartAt = ProviderTimeMapper.toInstant(trialStart);
            }
            return trialStartAt;
        }

        public LocalDateTime trialStartUtc() {
            return ProviderTimeMapper.toUtcLocalDateTime(trialStartInstant());
        }

        public Instant trialEndInstant() {
            if (trialEndAt == null) {
                trialEndAt = ProviderTimeMapper.toInstant(trialEnd);
            }
            return trialEndAt;
        }

        public LocalDateTime trialEndUtc() {
            return ProviderTimeMapper.toUtcLocalDateTime(trialEndInstant());
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

        private Long resolveCurrentPeriodStartEpoch() {
            if (currentPeriodStart != null) {
                return currentPeriodStart;
            }
            if (items == null || items.getData() == null || items.getData().isEmpty()) {
                return null;
            }
            return items.getData().stream()
                    .map(SubscriptionItem::getCurrentPeriodStart)
                    .filter(value -> value != null)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
        }

        private Long resolveCurrentPeriodEndEpoch() {
            if (currentPeriodEnd != null) {
                return currentPeriodEnd;
            }
            if (items == null || items.getData() == null || items.getData().isEmpty()) {
                return null;
            }
            return items.getData().stream()
                    .map(SubscriptionItem::getCurrentPeriodEnd)
                    .filter(value -> value != null)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
        }
    }

    @Data
    public static class SubscriptionItems {
        @JsonProperty("data")
        private List<SubscriptionItem> data;
    }

    @Data
    public static class SubscriptionItem {
        @JsonProperty("id")
        private String id;

        @JsonProperty("current_period_start")
        @SerializedName("current_period_start")
        private Long currentPeriodStart;

        @JsonProperty("current_period_end")
        @SerializedName("current_period_end")
        private Long currentPeriodEnd;

        @JsonProperty("quantity")
        private Long quantity;

        @JsonProperty("price")
        private SubscriptionPrice price;
    }

    @Data
    public static class SubscriptionPrice {
        @JsonProperty("id")
        private String id;

        @JsonProperty("product")
        private String product;

        @JsonProperty("unit_amount")
        @SerializedName("unit_amount")
        private Long unitAmount;

        @JsonProperty("recurring")
        private Recurring recurring;
    }

    @Data
    public static class Recurring {
        @JsonProperty("interval")
        private String interval;
    }

    @Data
    public static class PauseCollection {
        @JsonProperty("behavior")
        private String behavior;

        @JsonProperty("resumes_at")
        @SerializedName("resumes_at")
        private Long resumesAt;
    }

    @Data
    public static class PendingUpdate {
        @JsonProperty("billing_cycle_anchor")
        @SerializedName("billing_cycle_anchor")
        private Long billingCycleAnchor;
    }

    public boolean isActive() {
        return object != null
                && "active".equals(object.status);
    }

    public boolean isInTrial() {
        if (object == null) {
            return false;
        }
        Instant trialEndInstant = object.trialEndInstant();
        return trialEndInstant != null && Instant.now().isBefore(trialEndInstant);
    }

    public boolean isCanceled() {
        return object != null
                && "canceled".equals(object.status);
    }

    public String getBusinessSubscriptionId() {
        if (object == null || object.metadata == null) {
            return null;
        }
        return object.metadata.get("business_subscription_id");
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

    // Parse ProviderRawEvent.rawObjectJson for customer.subscription.* handlers.
    public static SubscriptionObject parseObject(String rawObjectJson) {
        if (rawObjectJson == null || rawObjectJson.isBlank()) {
            return null;
        }
        return WEBHOOK_GSON.fromJson(rawObjectJson, SubscriptionObject.class);
    }

    public static SubscriptionWebhookEvent parseProviderRawEvent(ProviderRawEvent providerRawEvent) {
        SubscriptionObject subscriptionObject = parseObject(providerRawEvent.getRawObjectJson());
        SubscriptionWebhookEvent event = SubscriptionWebhookEvent.builder()
                .eventId(providerRawEvent.getEventId())
                .eventType(providerRawEvent.getType())
                .created(providerRawEvent.getCreated())
                .provider(providerRawEvent.getProvider())
                .object(subscriptionObject)
                .build();
        hydrateInstants(event);
        return event;
    }

    private static void hydrateInstants(SubscriptionWebhookEvent event) {
        if (event == null) {
            return;
        }
        event.createdAt = ProviderTimeMapper.toInstant(event.created);

        SubscriptionObject object = event.object;
        if (object == null) {
            return;
        }

        object.currentPeriodStartAt = object.currentPeriodStartInstant();
        object.currentPeriodEndAt = object.currentPeriodEndInstant();
        object.trialStartAt = ProviderTimeMapper.toInstant(object.trialStart);
        object.trialEndAt = ProviderTimeMapper.toInstant(object.trialEnd);
        object.createdAt = ProviderTimeMapper.toInstant(object.created);
    }
}

