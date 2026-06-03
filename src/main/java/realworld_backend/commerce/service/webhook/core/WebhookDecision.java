package realworld_backend.commerce.service.webhook.core;

import org.springframework.http.HttpStatus;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.model.EventStatus;

public record WebhookDecision(
        boolean terminal,
        EventStatus eventStatus,
        HttpStatus httpStatus,
        boolean needUpsertAbnormal,
        AbnormalOrderType abnormalOrderType
) {
}
