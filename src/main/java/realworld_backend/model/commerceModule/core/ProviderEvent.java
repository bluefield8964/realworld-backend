package realworld_backend.model.commerceModule.core;

import com.google.gson.annotations.SerializedName;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
public class ProviderEvent {

    // ===== event envelope =====
    private String provider;      // stripe
    private String id;            // eventId
    private String type;          // eventType
    private Long created;
    private Boolean livemode;

    // ===== payload =====
    private EventData data;
    @Builder
    @Data
    public static class EventData {
        private ProviderSession object;
    }

    @Builder
    @Data
    public static class ProviderSession {
        // common object info
        private String id;                // session id / payment_intent id
        private String object;            // checkout.session / payment_intent
        private String status;
        @SerializedName("payment_status")// complete/open/succeeded/failed
        private String paymentStatus;     // paid/unpaid

        // fields used by your WebhookService
        @SerializedName("amount_total")
        private Long amountTotal;
        private String currency;
        private String customer;
        @SerializedName("customer_email")
        private String customerEmail;
        @SerializedName("payment_intent")
        private String paymentIntent;
        @SerializedName("client_reference_id")
        private String clientReferenceId;
        @SerializedName("metadata")
        private Map<String, String> metadata;
    }


    // ===== helper methods for current flow =====
    public boolean isCheckoutCompleted() {
        return "checkout.session.completed".equals(type);
    }

    public boolean isCheckoutAsyncFailed() {
        return "checkout.session.async_payment_failed".equals(type);
    }

    public boolean isActionableForCurrentFlow() {
        return isCheckoutCompleted() || isCheckoutAsyncFailed();
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

    public static ProviderEvent fromProviderEvent(Event event,String provider) {
        String json = event.getData().getObject().toJson();
        ProviderSession obj = Session.GSON.fromJson(json, ProviderSession.class);

        return ProviderEvent.builder()
                .provider(provider)
                .id(event.getId())
                .type(event.getType())
                .created(event.getCreated())
                .livemode(event.getLivemode())
                .data(EventData.builder().object(obj).build())
                .build();
    }

}
