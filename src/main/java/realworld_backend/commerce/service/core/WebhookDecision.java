package realworld_backend.commerce.service.core;

import org.springframework.http.HttpStatus;
import realworld_backend.commerce.model.AbnormalOrderStatus;
import realworld_backend.commerce.model.StripeEventStatus;

public record WebhookDecision(
        boolean terminal,
        StripeEventStatus stripeEventStatus,
        HttpStatus httpStatus,
        boolean upsertAbnormal,
        AbnormalOrderStatus abnormalOrderStatus
) {
}
