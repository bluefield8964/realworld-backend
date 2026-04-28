package realworld_backend.commerce.model.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.stripe.model.Event;
import lombok.Builder;
import lombok.Data;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.service.core.EventAnticorruptionLayer;
import realworld_backend.commerce.service.core.ProviderTimeMapper;

import java.time.Instant;
import java.util.Map;

@Builder
@Data
public class ProviderEvent {
    private static final Gson WEBHOOK_GSON = new GsonBuilder().create();

    // Original provider envelope, normalized for application flow.
    private String provider;
    private String eventId;
    private BusinessEventType type;
    private Long created;
    private transient Instant createdAt;
    private Boolean livemode;

    // Normalized object payload.
    private EventData data;

    @Builder
    @Data
    public static class EventData {
        private ProviderSession object;
    }

    @Builder
    @Data
    public static class ProviderSession {
        // Common object fields
        private String id;                // session id / payment_intent id
        private String object;            // checkout.session / payment_intent
        private String status;

        @SerializedName("payment_status")
        private String paymentStatus;     // complete/open/succeeded/failed

        @SerializedName("mode")
        private String mode;              // payment/subscription

        @SerializedName("url")
        private String url;

        // Amount and currency
        @SerializedName("amount_total")
        private Long amountTotal;

        @SerializedName("currency")
        private String currency;

        // Customer details
        @SerializedName("customer")
        private String customer;

        @SerializedName("customer_email")
        private String customerEmail;

        // Payment identifiers
        @SerializedName("payment_intent")
        private String paymentIntent;     // present for one-time payment flows

        @SerializedName("subscription")
        private String subscription;      // present for subscription flows

        @SerializedName("setup_intent")
        private String setupIntent;

        // Business correlation fields
        @SerializedName("client_reference_id")
        private String clientReferenceId;

        @SerializedName("metadata")
        private Map<String, String> metadata;
    }

    public String sessionId() {
        return data != null && data.object != null ? data.object.id : null;
    }

    public String orderNo() {
        return data != null && data.object != null && data.object.metadata != null
                ? data.object.metadata.get("orderNo")
                : null;
    }

    public String userId() {
        return data != null && data.object != null && data.object.metadata != null
                ? data.object.metadata.get("userId")
                : null;
    }

    public String productId() {
        return data != null && data.object != null && data.object.metadata != null
                ? data.object.metadata.get("product")
                : null;
    }

    public static ProviderEvent fromProviderEvent(Event event, String provider) {
        String json = event.getData().getObject().toJson();
        ProviderSession obj = WEBHOOK_GSON.fromJson(json, ProviderSession.class);
        BusinessEventType eventType = EventAnticorruptionLayer.convertStripeEvent(event.getType(), json);

        return ProviderEvent.builder()
                .provider(provider)
                .eventId(event.getId())
                .type(eventType)
                .created(event.getCreated())
                .createdAt(ProviderTimeMapper.toInstant(event.getCreated()))
                .livemode(event.getLivemode())
                .data(EventData.builder().object(obj).build())
                .build();
    }

    public Instant createdAtInstant() {
        if (createdAt == null) {
            createdAt = ProviderTimeMapper.toInstant(created);
        }
        return createdAt;
    }

    // Business helper used by existing subscription checks.
    public boolean isSubscriptionMode() {
        return "subscription".equals(data.object.mode);
    }
}
