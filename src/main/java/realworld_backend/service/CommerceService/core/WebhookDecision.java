package realworld_backend.service.CommerceService.core;

import org.springframework.http.HttpStatus;
import realworld_backend.model.commerceModule.AbnormalOrderStatus;
import realworld_backend.model.commerceModule.StripeEventStatus;

public record WebhookDecision(
        boolean terminal,
        StripeEventStatus stripeEventStatus,
        HttpStatus httpStatus,
        boolean upsertAbnormal,
        AbnormalOrderStatus abnormalOrderStatus
) {
}