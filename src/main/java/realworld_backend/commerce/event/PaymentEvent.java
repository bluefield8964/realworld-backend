package realworld_backend.commerce.event;

import java.time.LocalDateTime;

public class PaymentEvent {

    /**
     * Internal normalized event type used by this system.
     */
    private BusinessEventType eventType;

    /**
     * Payment provider channel: STRIPE / PAYPAL / ALIPAY / WECHAT_PAY.
     */
    private PaymentProvider provider;

    /**
     * Provider-side event ID, used for idempotency.
     */
    private String providerEventId;

    /**
     * Raw provider event type.
     * Example: Stripe `payment_intent.succeeded`.
     */
    private String providerEventType;

    /**
     * Time when the event happened.
     */
    private LocalDateTime occurredAt;

    /**
     * Whether the event comes from live mode.
     */
    private boolean liveMode;

    /**
     * Raw payload for troubleshooting.
     */
    private String rawPayload;
}

enum PaymentProvider {
    STRIPE,
    PAYPAL,
    ALIPAY,
    WECHAT_PAY
}
