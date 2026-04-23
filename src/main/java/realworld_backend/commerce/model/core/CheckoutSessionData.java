package realworld_backend.commerce.model.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stripe.model.checkout.Session;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class CheckoutSessionData {

    public static CheckoutSessionData generateStripeCheckoutSessionData(Session stripe) {
        if (stripe == null) {
            throw new IllegalArgumentException("stripe session must not be null");
        }
        return CheckoutSessionData.builder()
                .id(stripe.getId())
                .url(stripe.getUrl())
                .paymentStatus(stripe.getPaymentStatus())
                .status(stripe.getStatus())
                .amountTotal(stripe.getAmountTotal())
                .currency(stripe.getCurrency())
                .customer(stripe.getCustomer())
                .metadata(stripe.getMetadata())
                .paymentIntent(stripe.getPaymentIntent())
                .build();
    }

    // ============ 閺堚偓闁插秷顩﹂惃鍕摟濞?============

    @JsonProperty("id")
    private String id;                      // Session ID - 閸烆垯绔撮弽鍥槕缁?

    @JsonProperty("status")
    private String status;                  // "open", "complete", "expired"

    @JsonProperty("payment_status")
    private String paymentStatus;           // "paid", "unpaid", "no_payment_required"

    @JsonProperty("url")
    private String url;                     // 闁插秴鐣鹃崥鎴濆煂閻ㄥ嫮绮ㄧ拹锕傘€夐棃顢籖L

    // ============ 娑撴艾濮熼崗鎶芥暛鐎涙顔?============

    @JsonProperty("amount_total")
    private Long amountTotal;               // 閹鍣炬０婵撶礄閸掑棴绱?

    @JsonProperty("currency")
    private String currency;                // 鐠愌冪娴狅絿鐖?

    @JsonProperty("customer")
    private String customer;                // 鐎广垺鍩汭D

    @JsonProperty("customer_email")
    private String customerEmail;           // 鐎广垺鍩涢柇顔绢唸

    @JsonProperty("payment_intent")
    private String paymentIntent;           // PaymentIntent ID

    @JsonProperty("client_reference_id")
    private String clientReferenceId;       // 娴ｇ姷娈戠拋銏犲礋瀵洜鏁D

    @JsonProperty("metadata")
    private Map<String, String> metadata;   // 閼奉亜鐣炬稊澶夌瑹閸斺剝鏆熼幑?

    // ============ 閺冨爼妫跨€涙顔?============

    @JsonProperty("created")
    private Long created;                   // 閸掓稑缂撻弮鍫曟？閹?

    @JsonProperty("expires_at")
    private Long expiresAt;                 // 鏉╁洦婀￠弮鍫曟？閹?

    // ============ URL鐎涙顔?============

    @JsonProperty("success_url")
    private String successUrl;              // 閹存劕濮涙い鐢告桨URL

    @JsonProperty("cancel_url")
    private String cancelUrl;               // 閸欐牗绉锋い鐢告桨URL

    // ============ 閸欘垶鈧绲鹃張澶屾暏閻ㄥ嫬鐡у▓?============

    @JsonProperty("mode")
    private String mode;                    // "payment", "setup", "subscription"

    @JsonProperty("object")
    private String object;                  // 閸ュ搫鐣鹃崐?"checkout.session"

    @JsonProperty("livemode")
    private Boolean livemode;               // 閺勵垰鎯侀悽鐔堕獓閻滎垰顣?

    @JsonProperty("customer_details")
    private CustomerDetails customerDetails; // 鐎广垺鍩涚拠锔剧矎娣団剝浼?

    @JsonProperty("subscription")
    private String subscription;            // 鐠併垽妲処D閿涘牐顓归梼鍛佸蹇旀閿?

    // 瀹撳苯顨滅猾?
    public static class CustomerDetails {
        @JsonProperty("email")
        private String email;

        @JsonProperty("name")
        private String name;

        @JsonProperty("phone")
        private String phone;

    }
}

