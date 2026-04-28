package realworld_backend.commerce.event;

import java.math.BigDecimal;

public class PaymentSucceededEvent extends PaymentEvent {

    private String paymentId;

    private String orderId;

    private String customerId;

    private BigDecimal amount;

    private String currency;

    private String transactionId;
}