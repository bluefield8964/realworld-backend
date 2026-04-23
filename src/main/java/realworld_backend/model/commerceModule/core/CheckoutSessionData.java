package realworld_backend.model.commerceModule.core;

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

    // ============ 最重要的字段 ============

    @JsonProperty("id")
    private String id;                      // Session ID - 唯一标识符

    @JsonProperty("status")
    private String status;                  // "open", "complete", "expired"

    @JsonProperty("payment_status")
    private String paymentStatus;           // "paid", "unpaid", "no_payment_required"

    @JsonProperty("url")
    private String url;                     // 重定向到的结账页面URL

    // ============ 业务关键字段 ============

    @JsonProperty("amount_total")
    private Long amountTotal;               // 总金额（分）

    @JsonProperty("currency")
    private String currency;                // 货币代码

    @JsonProperty("customer")
    private String customer;                // 客户ID

    @JsonProperty("customer_email")
    private String customerEmail;           // 客户邮箱

    @JsonProperty("payment_intent")
    private String paymentIntent;           // PaymentIntent ID

    @JsonProperty("client_reference_id")
    private String clientReferenceId;       // 你的订单引用ID

    @JsonProperty("metadata")
    private Map<String, String> metadata;   // 自定义业务数据

    // ============ 时间字段 ============

    @JsonProperty("created")
    private Long created;                   // 创建时间戳

    @JsonProperty("expires_at")
    private Long expiresAt;                 // 过期时间戳

    // ============ URL字段 ============

    @JsonProperty("success_url")
    private String successUrl;              // 成功页面URL

    @JsonProperty("cancel_url")
    private String cancelUrl;               // 取消页面URL

    // ============ 可选但有用的字段 ============

    @JsonProperty("mode")
    private String mode;                    // "payment", "setup", "subscription"

    @JsonProperty("object")
    private String object;                  // 固定值 "checkout.session"

    @JsonProperty("livemode")
    private Boolean livemode;               // 是否生产环境

    @JsonProperty("customer_details")
    private CustomerDetails customerDetails; // 客户详细信息

    @JsonProperty("subscription")
    private String subscription;            // 订阅ID（订阅模式时）

    // 嵌套类
    public static class CustomerDetails {
        @JsonProperty("email")
        private String email;

        @JsonProperty("name")
        private String name;

        @JsonProperty("phone")
        private String phone;

    }
}
