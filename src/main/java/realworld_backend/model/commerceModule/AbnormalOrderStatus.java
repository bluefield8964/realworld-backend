package realworld_backend.model.commerceModule;

public enum AbnormalOrderStatus {
    FIXED,                // 已修复（本地订单已和Stripe同步）
    UNPAID_CONFIRMED,     // 已确认未支付/支付失败
    RETRY_EXHAUSTED,      // 自动重试次数已用尽
    ORDER_MISSING,
    PAYMENT_MISSING,
    RE_PENDING,           // abnormal order is re processing
    MANUAL_REVIEW         //need manual review
}
