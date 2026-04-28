package realworld_backend.commerce.event;

import java.math.BigDecimal;

public class RefundSucceededEvent extends PaymentEvent {

    private String refundId;

    private String paymentId;

    private String orderId;

    private BigDecimal amount;

    private String currency;
}