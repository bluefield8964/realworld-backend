package realworld_backend.commerce.model.invoice;

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
import java.util.List;
import java.util.Map;

@Builder
@Data
public class InvoiceWebhookEvent implements ProviderEventInterface {
    private static final Gson WEBHOOK_GSON = new GsonBuilder().create();

    @JsonProperty("id")
    private String eventId;

    @JsonProperty("type")
    private BusinessEventType eventType; // invoice.*

    private String provider;

    @JsonProperty("created")
    private Long created;
    private transient Instant createdAt;

    @JsonProperty("object")
    private InvoiceObject object;

    @Data
    public static class InvoiceObject {
        @JsonProperty("id")
        private String id; // Invoice ID

        @JsonProperty("object")
        private String object; // invoice

        @JsonProperty("status")
        private String status; // draft/open/paid/void/uncollectible

        @JsonProperty("paid")
        private Boolean paid;

        @JsonProperty("customer")
        private String customer;

        @JsonProperty("subscription")
        private String subscription;

        @JsonProperty("payment_intent")
        @SerializedName("payment_intent")
        private String paymentIntent;

        @JsonProperty("amount_due")
        @SerializedName("amount_due")
        private Long amountDue;

        @JsonProperty("amount_paid")
        @SerializedName("amount_paid")
        private Long amountPaid;

        @JsonProperty("amount_remaining")
        @SerializedName("amount_remaining")
        private Long amountRemaining;

        @JsonProperty("subtotal")
        private Long subtotal;

        @JsonProperty("total")
        private Long total;

        @JsonProperty("tax")
        private Long tax;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("period_start")
        @SerializedName("period_start")
        private Long periodStart;

        @JsonProperty("period_end")
        @SerializedName("period_end")
        private Long periodEnd;

        @JsonProperty("last_payment_error")
        @SerializedName("last_payment_error")
        private PaymentError lastPaymentError;

        @JsonProperty("attempt_count")
        @SerializedName("attempt_count")
        private Integer attemptCount;

        @JsonProperty("next_payment_attempt")
        @SerializedName("next_payment_attempt")
        private Long nextPaymentAttempt;

        @JsonProperty("lines")
        private InvoiceLines lines;

        @JsonProperty("charge")
        private String charge;

        @JsonProperty("receipt_url")
        @SerializedName("receipt_url")
        private String receiptUrl;

        @JsonProperty("created")
        private Long created;

        @JsonProperty("finalized_at")
        @SerializedName("finalized_at")
        private Long finalizedAt;

        @JsonProperty("paid_at")
        @SerializedName("paid_at")
        private Long paidAt;

        @JsonProperty("due_date")
        @SerializedName("due_date")
        private Long dueDate;

        @JsonProperty("metadata")
        private Map<String, String> metadata;

        @JsonProperty("subscription_details")
        @SerializedName("subscription_details")
        private SubscriptionDetails subscriptionDetails;
        private transient Instant periodStartAt;
        private transient Instant periodEndAt;
        private transient Instant nextPaymentAttemptAt;
        private transient Instant createdAt;

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

        // Stripe invoices for subscriptions may carry business metadata under
        // subscription_details.metadata instead of invoice.metadata.
        public String resolveMetadataValue(String key) {
            if (subscriptionDetails != null
                    && subscriptionDetails.metadata != null
                    && subscriptionDetails.metadata.get(key) != null
                    && !subscriptionDetails.metadata.get(key).isBlank()) {
                return subscriptionDetails.metadata.get(key);
            }
            if (metadata != null && metadata.get(key) != null && !metadata.get(key).isBlank()) {
                return metadata.get(key);
            }
            return null;
        }
    }

    @Data
    public static class SubscriptionDetails {
        @JsonProperty("metadata")
        private Map<String, String> metadata;
    }

    @Data
    public static class PaymentError {
        @JsonProperty("code")
        private String code;

        @JsonProperty("decline_code")
        @SerializedName("decline_code")
        private String declineCode;

        @JsonProperty("message")
        private String message;
    }

    @Data
    public static class InvoiceLines {
        @JsonProperty("data")
        private List<InvoiceLineItem> data;
    }

    @Data
    public static class InvoiceLineItem {
        @JsonProperty("id")
        private String id;

        @JsonProperty("type")
        private String type;

        @JsonProperty("subscription")
        private String subscription;

        @JsonProperty("subscription_item")
        @SerializedName("subscription_item")
        private String subscriptionItem;

        @JsonProperty("price")
        private InvoicePrice price;
    }

    @Data
    public static class InvoicePrice {
        @JsonProperty("id")
        private String id;

        @JsonProperty("recurring")
        private Recurring recurring;
    }

    @Data
    public static class Recurring {
        @JsonProperty("interval")
        private String interval;
    }

    public boolean isPaymentSuccessful() {
        return object != null
                && "paid".equals(object.status)
                && Boolean.TRUE.equals(object.paid);
    }

    public boolean isPaymentFailed() {
        return object != null
                && "open".equals(object.status)
                && object.lastPaymentError != null;
    }

    public boolean isSubscriptionInvoice() {
        return object != null
                && object.subscription != null;
    }

    public String getFailureReason() {
        if (object == null || object.lastPaymentError == null) {
            return null;
        }
        return object.lastPaymentError.message;
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

    // Parse ProviderRawEvent.rawObjectJson for invoice.* handlers.
    public static InvoiceObject parseObject(String rawObjectJson) {
        if (rawObjectJson == null || rawObjectJson.isBlank()) {
            return null;
        }
        return WEBHOOK_GSON.fromJson(rawObjectJson, InvoiceObject.class);
    }

    public static InvoiceWebhookEvent parseProviderRawEvent(ProviderRawEvent providerRawEvent) {
        InvoiceObject invoiceObject = parseObject(providerRawEvent.getRawObjectJson());
        InvoiceWebhookEvent event = InvoiceWebhookEvent.builder()
                .eventId(providerRawEvent.getEventId())
                .eventType(providerRawEvent.getType())
                .created(providerRawEvent.getCreated())
                .provider(providerRawEvent.getProvider())
                .object(invoiceObject)
                .build();
        hydrateInstants(event);
        return event;
    }

    private static void hydrateInstants(InvoiceWebhookEvent event) {
        if (event == null) {
            return;
        }
        event.createdAt = ProviderTimeMapper.toInstant(event.created);

        InvoiceObject object = event.object;
        if (object == null) {
            return;
        }
        object.periodStartAt = ProviderTimeMapper.toInstant(object.periodStart);
        object.periodEndAt = ProviderTimeMapper.toInstant(object.periodEnd);
        object.nextPaymentAttemptAt = ProviderTimeMapper.toInstant(object.nextPaymentAttempt);
        object.createdAt = ProviderTimeMapper.toInstant(object.created);
    }
}

