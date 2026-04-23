package realworld_backend.model.commerceModule.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stripe.model.checkout.Session;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
public class ProviderSession {


    // ============ 核心状态字段（必须） ============
    @JsonProperty("id")
    private String id;                          // Session ID

    @JsonProperty("status")
    private String status;                      // "open", "complete", "expired"

    @JsonProperty("payment_status")
    private String paymentStatus;               // "paid", "unpaid", "no_payment_required"

    @JsonProperty("object")
    private String object;                      // "checkout.session"

    // ============ 业务核心字段 ============
    @JsonProperty("amount_total")
    private Long amountTotal;                   // 总金额（分）

    @JsonProperty("currency")
    private String currency;                    // 货币代码

    @JsonProperty("customer")
    private String customer;                    // Stripe 客户ID

    @JsonProperty("customer_email")
    private String customerEmail;               // 客户邮箱

    @JsonProperty("payment_intent")
    private String paymentIntent;               // PaymentIntent ID

    @JsonProperty("client_reference_id")
    private String clientReferenceId;           // 你的订单引用ID

    @JsonProperty("metadata")
    private Map<String, String> metadata;       // 自定义业务数据

    // ============ 时间字段 ============
    @JsonProperty("created")
    private Long created;                       // 创建时间戳

    @JsonProperty("expires_at")
    private Long expiresAt;                     // 过期时间戳

    // ============ 客户详情（可选但有用） ============
    @JsonProperty("customer_details")
    private CustomerDetails customerDetails;

    // ============ URL字段（可选） ============
    @JsonProperty("url")
    private String url;                         // 结账页面URL

    @JsonProperty("success_url")
    private String successUrl;                  // 成功跳转URL

    @JsonProperty("cancel_url")
    private String cancelUrl;                   // 取消跳转URL

    // ============ 其他有用字段 ============
    @JsonProperty("mode")
    private String mode;                        // "payment", "subscription", "setup"

    @JsonProperty("livemode")
    private Boolean livemode;                   // 是否生产环境

    @JsonProperty("subscription")
    private String subscription;                // 订阅ID（如果是订阅模式）


    // ============ 嵌套类：客户详情 ============
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

    // ============ 嵌套类：地址信息 ============
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

    // ============ 业务判断方法 ============
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
     * 是否支付成功
     */
    public boolean isPaymentSuccessful() {
        return "complete".equals(status) && "paid".equals(paymentStatus);
    }

    /**
     * 是否支付失败
     */
    public boolean isPaymentFailed() {
        return "open".equals(status) && "unpaid".equals(paymentStatus) && isExpired();
    }

    /**
     * 是否已过期
     */
    public boolean isExpired() {
        if (expiresAt == null) return false;
        return System.currentTimeMillis() / 1000 > expiresAt;
    }

    /**
     * 是否还可以支付
     */
    public boolean isPaymentPending() {
        return "open".equals(status) && !isExpired();
    }

    /**
     * 获取客户姓名（优先从详情获取）
     */
    public String getCustomerName() {
        if (customerDetails != null && customerDetails.name != null) {
            return customerDetails.name;
        }
        return null;
    }

    /**
     * 获取主要邮箱（优先级：customerEmail > customerDetails.email）
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

    // ============ 所有字段的 getters and setters ============
    // ... (省略，但需要包含所有字段的getter/setter)
}