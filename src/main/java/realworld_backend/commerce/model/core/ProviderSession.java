package realworld_backend.commerce.model.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stripe.model.checkout.Session;
import lombok.Builder;
import lombok.Data;
import realworld_backend.commerce.service.core.ProviderTimeMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@Builder
@Data
public class ProviderSession {

    @JsonProperty("id")
    private String id;                          // Checkout session id

    @JsonProperty("status")
    private String status;                      // open / complete / expired

    @JsonProperty("payment_status")
    private String paymentStatus;               // paid / unpaid / no_payment_required

    @JsonProperty("object")
    private String object;

    @JsonProperty("amount_total")
    private Long amountTotal;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("customer")
    private String customer;

    @JsonProperty("customer_email")
    private String customerEmail;

    @JsonProperty("payment_intent")
    private String paymentIntent;

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

    @JsonProperty("customer_details")
    private CustomerDetails customerDetails;

    @JsonProperty("url")
    private String url;

    @JsonProperty("success_url")
    private String successUrl;

    @JsonProperty("cancel_url")
    private String cancelUrl;

    @JsonProperty("mode")
    private String mode;

    @JsonProperty("livemode")
    private Boolean livemode;

    @JsonProperty("subscription")
    private String subscription;

    @Builder
    public static class CustomerDetails {
        @JsonProperty("email")
        private String email;

        @JsonProperty("name")
        private String name;

        @JsonProperty("phone")
        private String phone;

        @JsonProperty("address")
        private Address address;

        @JsonProperty("tax_exempt")
        private String taxExempt;
    }

    @Builder
    public static class Address {
        @JsonProperty("city")
        private String city;

        @JsonProperty("country")
        private String country;

        @JsonProperty("line1")
        private String line1;

        @JsonProperty("line2")
        private String line2;

        @JsonProperty("postal_code")
        private String postalCode;

        @JsonProperty("state")
        private String state;
    }

    // Maps Stripe session into provider-neutral session structure.
    public static ProviderSession mapToProviderSession(Session session) {
        ProviderSession.CustomerDetails customerDetails = null;
        if (session.getCustomerDetails() != null) {
            ProviderSession.Address address = null;
            if (session.getCustomerDetails().getAddress() != null) {
                address = ProviderSession.Address.builder()
                        .city(session.getCustomerDetails().getAddress().getCity())
                        .country(session.getCustomerDetails().getAddress().getCountry())
                        .line1(session.getCustomerDetails().getAddress().getLine1())
                        .line2(session.getCustomerDetails().getAddress().getLine2())
                        .postalCode(session.getCustomerDetails().getAddress().getPostalCode())
                        .state(session.getCustomerDetails().getAddress().getState())
                        .build();
            }

            customerDetails = ProviderSession.CustomerDetails.builder()
                    .email(session.getCustomerDetails().getEmail())
                    .name(session.getCustomerDetails().getName())
                    .phone(session.getCustomerDetails().getPhone())
                    .address(address)
                    .taxExempt(session.getCustomerDetails().getTaxExempt())
                    .build();
        }

        return ProviderSession.builder()
                .id(session.getId())
                .status(session.getStatus())
                .paymentStatus(session.getPaymentStatus())
                .object(session.getObject())
                .amountTotal(session.getAmountTotal())
                .currency(session.getCurrency())
                .customer(session.getCustomer())
                .customerEmail(session.getCustomerEmail())
                .paymentIntent(session.getPaymentIntent())
                .clientReferenceId(session.getClientReferenceId())
                .metadata(session.getMetadata())
                .created(session.getCreated())
                .createdAt(ProviderTimeMapper.toInstant(session.getCreated()))
                .expiresAt(session.getExpiresAt())
                .expiresAtTime(ProviderTimeMapper.toInstant(session.getExpiresAt()))
                .customerDetails(customerDetails)
                .url(session.getUrl())
                .successUrl(session.getSuccessUrl())
                .cancelUrl(session.getCancelUrl())
                .mode(session.getMode() == null ? null : session.getMode())
                .livemode(session.getLivemode())
                .subscription(session.getSubscription())
                .build();
    }

    // True when checkout is completed and paid.
    public boolean isPaymentSuccessful() {
        return "complete".equals(status) && "paid".equals(paymentStatus);
    }

    // Treat expired, unpaid checkout as payment failure.
    public boolean isPaymentFailed() {
        return "open".equals(status) && "unpaid".equals(paymentStatus) && isExpired();
    }

    // Session expiration check using epoch seconds.
    public boolean isExpired() {
        Instant expiresAtInstant = expiresAtInstant();
        return expiresAtInstant != null && Instant.now().isAfter(expiresAtInstant);
    }

    // Session is still open and not expired.
    public boolean isPaymentPending() {
        return "open".equals(status) && !isExpired();
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

    // Prefer customer_details.name when present.
    public String getCustomerName() {
        if (customerDetails != null && customerDetails.name != null) {
            return customerDetails.name;
        }
        return null;
    }

    // Prefer customerEmail, then fallback to customer_details.email.
    public String getPrimaryEmail() {
        if (customerEmail != null && !customerEmail.isEmpty()) {
            return customerEmail;
        }
        if (customerDetails != null && customerDetails.email != null) {
            return customerDetails.email;
        }
        return null;
    }
}
