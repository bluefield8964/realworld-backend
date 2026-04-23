package realworld_backend.commerce.model.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stripe.model.checkout.Session;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
public class ProviderSession {


    // ============ 閺嶇绺鹃悩鑸碘偓浣哥摟濞堢绱欒箛鍛淬€忛敍?============
    @JsonProperty("id")
    private String id;                          // Session ID

    @JsonProperty("status")
    private String status;                      // "open", "complete", "expired"

    @JsonProperty("payment_status")
    private String paymentStatus;               // "paid", "unpaid", "no_payment_required"

    @JsonProperty("object")
    private String object;                      // "checkout.session"

    // ============ 娑撴艾濮熼弽绋跨妇鐎涙顔?============
    @JsonProperty("amount_total")
    private Long amountTotal;                   // 閹鍣炬０婵撶礄閸掑棴绱?

    @JsonProperty("currency")
    private String currency;                    // 鐠愌冪娴狅絿鐖?

    @JsonProperty("customer")
    private String customer;                    // Stripe 鐎广垺鍩汭D

    @JsonProperty("customer_email")
    private String customerEmail;               // 鐎广垺鍩涢柇顔绢唸

    @JsonProperty("payment_intent")
    private String paymentIntent;               // PaymentIntent ID

    @JsonProperty("client_reference_id")
    private String clientReferenceId;           // 娴ｇ姷娈戠拋銏犲礋瀵洜鏁D

    @JsonProperty("metadata")
    private Map<String, String> metadata;       // 閼奉亜鐣炬稊澶夌瑹閸斺剝鏆熼幑?

    // ============ 閺冨爼妫跨€涙顔?============
    @JsonProperty("created")
    private Long created;                       // 閸掓稑缂撻弮鍫曟？閹?

    @JsonProperty("expires_at")
    private Long expiresAt;                     // 鏉╁洦婀￠弮鍫曟？閹?

    // ============ 鐎广垺鍩涚拠锔藉剰閿涘牆褰查柅澶夌稻閺堝鏁ら敍?============
    @JsonProperty("customer_details")
    private CustomerDetails customerDetails;

    // ============ URL鐎涙顔岄敍鍫濆讲闁绱?============
    @JsonProperty("url")
    private String url;                         // 缂佹捁澶勬い鐢告桨URL

    @JsonProperty("success_url")
    private String successUrl;                  // 閹存劕濮涚捄瀹犳祮URL

    @JsonProperty("cancel_url")
    private String cancelUrl;                   // 閸欐牗绉风捄瀹犳祮URL

    // ============ 閸忔湹绮張澶屾暏鐎涙顔?============
    @JsonProperty("mode")
    private String mode;                        // "payment", "subscription", "setup"

    @JsonProperty("livemode")
    private Boolean livemode;                   // 閺勵垰鎯侀悽鐔堕獓閻滎垰顣?

    @JsonProperty("subscription")
    private String subscription;                // 鐠併垽妲処D閿涘牆顩ч弸婊勬Ц鐠併垽妲勫Ο鈥崇础閿?


    // ============ 瀹撳苯顨滅猾浼欑窗鐎广垺鍩涚拠锔藉剰 ============
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

        // getters and setters...
    }

    // ============ 瀹撳苯顨滅猾浼欑窗閸︽澘娼冩穱鈩冧紖 ============
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

        // getters and setters...
    }

    // ============ 娑撴艾濮熼崚銈嗘焽閺傝纭?============
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
                // core status
                .id(session.getId())
                .status(session.getStatus())
                .paymentStatus(session.getPaymentStatus())
                .object(session.getObject())

                // business core
                .amountTotal(session.getAmountTotal())
                .currency(session.getCurrency())
                .customer(session.getCustomer())
                .customerEmail(session.getCustomerEmail())
                .paymentIntent(session.getPaymentIntent())
                .clientReferenceId(session.getClientReferenceId())
                .metadata(session.getMetadata())

                // time
                .created(session.getCreated())
                .expiresAt(session.getExpiresAt())

                // optional details
                .customerDetails(customerDetails)
                .url(session.getUrl())
                .successUrl(session.getSuccessUrl())
                .cancelUrl(session.getCancelUrl())
                .mode(session.getMode() == null ? null : session.getMode())
                .livemode(session.getLivemode())
                .subscription(session.getSubscription())
                .build();
    }

    /**
     * 閺勵垰鎯侀弨顖欑帛閹存劕濮?
     */
    public boolean isPaymentSuccessful() {
        return "complete".equals(status) && "paid".equals(paymentStatus);
    }

    /**
     * 閺勵垰鎯侀弨顖欑帛婢惰精瑙?
     */
    public boolean isPaymentFailed() {
        return "open".equals(status) && "unpaid".equals(paymentStatus) && isExpired();
    }

    /**
     * 閺勵垰鎯佸鑼剁箖閺?
     */
    public boolean isExpired() {
        if (expiresAt == null) return false;
        return System.currentTimeMillis() / 1000 > expiresAt;
    }

    /**
     * 閺勵垰鎯佹潻妯哄讲娴犮儲鏁禒?
     */
    public boolean isPaymentPending() {
        return "open".equals(status) && !isExpired();
    }

    /**
     * 閼惧嘲褰囩€广垺鍩涙慨鎾虫倳閿涘牅绱崗鍫滅矤鐠囷附鍎忛懢宄板絿閿?
     */
    public String getCustomerName() {
        if (customerDetails != null && customerDetails.name != null) {
            return customerDetails.name;
        }
        return null;
    }

    /**
     * 閼惧嘲褰囨稉鏄忣洣闁喚顔堥敍鍫滅喘閸忓牏楠囬敍姝漸stomerEmail > customerDetails.email閿?
     */
    public String getPrimaryEmail() {
        if (customerEmail != null && !customerEmail.isEmpty()) {
            return customerEmail;
        }
        if (customerDetails != null && customerDetails.email != null) {
            return customerDetails.email;
        }
        return null;
    }

    // ============ 閹碘偓閺堝鐡у▓鐢垫畱 getters and setters ============
    // ... (閻胶鏆愰敍灞肩稻闂団偓鐟曚礁瀵橀崥顐ｅ閺堝鐡у▓鐢垫畱getter/setter)
}
