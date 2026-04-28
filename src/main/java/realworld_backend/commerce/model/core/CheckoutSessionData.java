package realworld_backend.commerce.model.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stripe.model.checkout.Session;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.commerce.service.core.ProviderTimeMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSessionData {

    // Common checkout fields
    @JsonProperty("id")
    private String id;

    @JsonProperty("mode")
    private String mode; // payment / subscription / setup

    @JsonProperty("status")
    private String status; // open / complete / expired

    @JsonProperty("payment_status")
    private String paymentStatus; // paid / unpaid / no_payment_required

    @JsonProperty("url")
    private String url;

    @JsonProperty("amount_subtotal")
    private Long amountSubtotal;

    @JsonProperty("amount_total")
    private Long amountTotal;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("customer")
    private String customer;

    @JsonProperty("customer_email")
    private String customerEmail;

    @JsonProperty("client_reference_id")
    private String clientReferenceId;

    @JsonProperty("metadata")
    private Map<String, String> metadata;

    @JsonProperty("created")
    private Long created;
    private transient Instant createdAt;

    @JsonProperty("expires_at")
    private Long expiresAt;
    private transient Instant expiresAtTime;

    @JsonProperty("success_url")
    private String successUrl;

    @JsonProperty("cancel_url")
    private String cancelUrl;

    @JsonProperty("object")
    private String object;

    @JsonProperty("livemode")
    private Boolean livemode;

    @JsonProperty("customer_details")
    private CustomerDetails customerDetails;

    // Payment/setup mode related fields
    @JsonProperty("payment_intent")
    private String paymentIntent;

    @JsonProperty("setup_intent")
    private String setupIntent;

    @JsonProperty("payment_method_types")
    private List<String> paymentMethodTypes;

    // Subscription mode related fields
    @JsonProperty("subscription")
    private String subscription;

    @JsonProperty("invoice")
    private String invoice;

    // Local helper flag for service logic
    private Boolean subscriptionMode;

    // Maps one-time payment Checkout Session (mode=payment) to local DTO.
    public static CheckoutSessionData generateCheckoutSessionData(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        if (!"payment".equals(session.getMode())) {
            throw new IllegalArgumentException("generateCheckoutSessionData only supports payment mode");
        }

        return baseBuilder(session)
                .paymentIntent(session.getPaymentIntent())
                .setupIntent(session.getSetupIntent())
                .subscription(null)
                .invoice(null)
                .subscriptionMode(false)
                .build();
    }

    // Maps subscription Checkout Session for subscription service usage.
    public static CheckoutSessionData generateSubscriptionSessionData(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }

        return baseBuilder(session)
                .subscription(session.getSubscription())
                .invoice(session.getInvoice())
                .paymentIntent(session.getPaymentIntent()) // may be null in subscription mode
                .setupIntent(session.getSetupIntent())
                .subscriptionMode(true)
                .build();
    }

    private static CheckoutSessionDataBuilder baseBuilder(Session session) {
        return CheckoutSessionData.builder()
                .id(session.getId())
                .mode(session.getMode())
                .status(session.getStatus())
                .paymentStatus(session.getPaymentStatus())
                .url(session.getUrl())
                .amountSubtotal(session.getAmountSubtotal())
                .amountTotal(session.getAmountTotal())
                .currency(session.getCurrency())
                .customer(session.getCustomer())
                .customerEmail(session.getCustomerEmail())
                .clientReferenceId(session.getClientReferenceId())
                .metadata(session.getMetadata())
                .created(session.getCreated())
                .createdAt(ProviderTimeMapper.toInstant(session.getCreated()))
                .expiresAt(session.getExpiresAt())
                .expiresAtTime(ProviderTimeMapper.toInstant(session.getExpiresAt()))
                .successUrl(session.getSuccessUrl())
                .cancelUrl(session.getCancelUrl())
                .object(session.getObject())
                .livemode(session.getLivemode())
                .paymentMethodTypes(session.getPaymentMethodTypes())
                .customerDetails(mapCustomerDetails(session));
    }

    private static CustomerDetails mapCustomerDetails(Session session) {
        if (session.getCustomerDetails() == null) {
            return null;
        }

        return CustomerDetails.builder()
                .email(session.getCustomerDetails().getEmail())
                .name(session.getCustomerDetails().getName())
                .phone(session.getCustomerDetails().getPhone())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerDetails {
        @JsonProperty("email")
        private String email;

        @JsonProperty("name")
        private String name;

        @JsonProperty("phone")
        private String phone;
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

    public Instant expiresAtInstant() {
        if (expiresAtTime == null) {
            expiresAtTime = ProviderTimeMapper.toInstant(expiresAt);
        }
        return expiresAtTime;
    }

    public LocalDateTime expiresAtUtc() {
        return ProviderTimeMapper.toUtcLocalDateTime(expiresAtInstant());
    }
}
