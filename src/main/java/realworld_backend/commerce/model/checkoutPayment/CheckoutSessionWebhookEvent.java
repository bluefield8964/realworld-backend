package realworld_backend.commerce.model.checkoutPayment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
public class CheckoutSessionWebhookEvent implements ProviderEventInterface {
    private static final Gson WEBHOOK_GSON = new GsonBuilder().create();

    @JsonProperty("id")
    private String eventId;

    @JsonProperty("type")
    private BusinessEventType eventType;// checkout.session.*


    private String provider;

    @JsonProperty("created")
    private Long created;
    private transient Instant createdAt;

    @JsonProperty("object")
    private CheckoutSessionObject object;

//    @Data
//    public static class SessionEventData {
//        @JsonProperty("object")
//        private CheckoutSessionObject object;
//    }

    @Data
    public static class CheckoutSessionObject {
        @JsonProperty("id")
        private String id; // Session ID

        @JsonProperty("status")
        private String status; // open/complete/expired

        @JsonProperty("payment_status")

        @SerializedName("payment_status")
        private String paymentStatus; // paid/unpaid/no_payment_required

        @JsonProperty("mode")
        private String mode; // payment/subscription

        @JsonProperty("url")
        private String url;

        @JsonProperty("amount_total")
        @SerializedName("amount_total")
        private Long amountTotal;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("customer")
        private JsonElement customer;

        @JsonProperty("customer_email")
        @SerializedName("customer_email")
        private String customerEmail;

        @JsonProperty("customer_details")
        @SerializedName("customer_details")
        private CustomerDetails customerDetails;

        @JsonProperty("client_reference_id")
        @SerializedName("client_reference_id")
        private String clientReferenceId;

        @JsonProperty("metadata")
        private Map<String, String> metadata;

        @JsonProperty("payment_intent")
        @SerializedName("payment_intent")
        private JsonElement paymentIntent;

        @JsonProperty("subscription")
        private JsonElement subscription;

        @JsonProperty("setup_intent")
        @SerializedName("setup_intent")
        private JsonElement setupIntent;

        @JsonProperty("subscription_data")
        @SerializedName("subscription_data")
        private SubscriptionData subscriptionData;

        @JsonProperty("line_items")
        @SerializedName("line_items")
        private LineItems lineItems;

        @JsonProperty("created")
        private Long created;

        @JsonProperty("expires_at")
        @SerializedName("expires_at")
        private Long expiresAt;
        private transient Instant createdAt;
        private transient Instant expiresAtTime;

        public Instant createdAtInstant() {
            if (createdAt == null) {
                createdAt = ProviderTimeMapper.toInstant(created);
            }
            return createdAt;
        }

        public LocalDateTime createdAtUtc() {
            return ProviderTimeMapper.toUtcLocalDateTime(createdAtInstant());
        }

        public Instant expiresAtInstant() {
            if (expiresAtTime == null) {
                expiresAtTime = ProviderTimeMapper.toInstant(expiresAt);
            }
            return expiresAtTime;
        }

        public LocalDateTime expiresAtUtc() {
            return ProviderTimeMapper.toUtcLocalDateTime(expiresAtInstant());
        }

        /**
         * Stripe expandable field: may be null, a string id, or an expanded object.
         */
        public String getCustomer() {
            return readExpandableId(customer);
        }

        /**
         * Stripe expandable field: may be null, a string id, or an expanded object.
         */
        public String getPaymentIntent() {
            return readExpandableId(paymentIntent);
        }

        /**
         * Stripe expandable field: may be null, a string id, or an expanded object.
         */
        public String getSubscription() {
            return readExpandableId(subscription);
        }

        /**
         * Stripe expandable field: may be null, a string id, or an expanded object.
         */
        public String getSetupIntent() {
            return readExpandableId(setupIntent);
        }

        private static String readExpandableId(JsonElement value) {
            if (value == null || value.isJsonNull()) {
                return null;
            }
            if (value.isJsonPrimitive()) {
                return value.getAsString();
            }
            if (value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                JsonElement id = object.get("id");
                if (id != null && !id.isJsonNull()) {
                    return id.getAsString();
                }
            }
            return null;
        }
    }

    @Data
    public static class CustomerDetails {
        @JsonProperty("email")
        private String email;

        @JsonProperty("name")
        private String name;

        @JsonProperty("phone")
        private String phone;
    }

    @Data
    public static class SubscriptionData {
        @JsonProperty("trial_period_days")
        @SerializedName("trial_period_days")
        private Long trialPeriodDays;

        @JsonProperty("trial_end")
        @SerializedName("trial_end")
        private Long trialEnd;

        @JsonProperty("metadata")
        private Map<String, String> metadata;
        private transient Instant trialEndAt;

        public Instant trialEndInstant() {
            if (trialEndAt == null) {
                trialEndAt = ProviderTimeMapper.toInstant(trialEnd);
            }
            return trialEndAt;
        }

        public LocalDateTime trialEndUtc() {
            return ProviderTimeMapper.toUtcLocalDateTime(trialEndInstant());
        }
    }

    @Data
    public static class LineItems {
        @JsonProperty("data")
        private List<LineItem> data;
    }

    @Data
    public static class LineItem {
        @JsonProperty("price")
        private Price price;
    }

    @Data
    public static class Price {
        @JsonProperty("id")
        private String id;

        @JsonProperty("type")
        private String type;

        @JsonProperty("recurring")
        private Recurring recurring;
    }

    @Data
    public static class Recurring {
        @JsonProperty("interval")
        private String interval;

        @JsonProperty("interval_count")
        @SerializedName("interval_count")
        private Integer intervalCount;
    }

    public boolean isSubscriptionMode() {
        return object != null
                && "subscription".equals(object.mode);
    }

    public boolean isPaymentSuccessful() {
        return object != null
                && "complete".equals(object.status)
                && "paid".equals(object.paymentStatus);
    }

    public String getBusinessId() {
        return object != null ? object.clientReferenceId : null;
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

    // Parse ProviderRawEvent.rawObjectJson for checkout.session.* handlers.
    public static CheckoutSessionObject parseObject(String rawObjectJson) {
        if (rawObjectJson == null || rawObjectJson.isBlank()) {
            return null;
        }
        return WEBHOOK_GSON.fromJson(rawObjectJson, CheckoutSessionObject.class);
    }

    public static CheckoutSessionWebhookEvent parseProviderRawEvent(ProviderRawEvent providerRawEvent){
        CheckoutSessionObject checkoutSessionObject = parseObject(providerRawEvent.getRawObjectJson());
        CheckoutSessionWebhookEvent event = CheckoutSessionWebhookEvent.builder().eventId(providerRawEvent.getEventId())
                .eventType(providerRawEvent.getType())
                .created(providerRawEvent.getCreated())
                .provider(providerRawEvent.getProvider())
                .object(checkoutSessionObject).build();
        hydrateInstants(event);
        return event;

    }

    private static void hydrateInstants(CheckoutSessionWebhookEvent event) {
        if (event == null) {
            return;
        }
        event.createdAt = ProviderTimeMapper.toInstant(event.created);
        CheckoutSessionObject object = event.object;
        if (object == null) {
            return;
        }
        object.createdAt = ProviderTimeMapper.toInstant(object.created);
        object.expiresAtTime = ProviderTimeMapper.toInstant(object.expiresAt);

        SubscriptionData subscriptionData = object.subscriptionData;
        if (subscriptionData != null) {
            subscriptionData.trialEndAt = ProviderTimeMapper.toInstant(subscriptionData.trialEnd);
        }
    }
}
