package realworld_backend.commerce.event;

import java.time.LocalDateTime;

public class SubscriptionActivatedEvent extends PaymentEvent {

    private String subscriptionId;

    private String customerId;

    private String planId;

    private LocalDateTime currentPeriodStart;

    private LocalDateTime currentPeriodEnd;
}