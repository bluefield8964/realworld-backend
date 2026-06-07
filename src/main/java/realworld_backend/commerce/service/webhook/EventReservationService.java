package realworld_backend.commerce.service.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.BusinessEvent;
import realworld_backend.commerce.model.EventStatus;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.model.log.AbnormalDomainType;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.service.AbnormalOrchestrator;
import realworld_backend.commerce.service.core.ProviderTimeMapper;
import realworld_backend.commerce.service.core.RetryPolicy;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.webhook.core.WebhookDecision;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.Instant;

/**
 * Owns event-row reservation and retry-budget takeover semantics before a
 * webhook handler starts business processing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventReservationService {
    private final BusinessEventService businessEventService;
    private final RetryPolicy retryPolicy;
    private final AbnormalOrchestrator abnormalOrchestrator;
    private final PaymentFailureEscalationService paymentFailureEscalationService;

    /**
     * Reserves event ownership or takes over stale ownership.
     * Returns true when the event has already been completed and caller should stop.
     */

    public boolean reserveOrTakeover(WebhookContext ctx) {
        String eventId = ctx.getEventId();
        BusinessEventType eventType = ctx.getEventType();
        try {
            // First-seen event: create PROCESSING slot.
            businessEventService.saveProcessing(eventId, ctx.getEventType());
            ctx.setAttempts(0);
            return false;
        } catch (DataIntegrityViolationException ignore) {
            BusinessEvent ev = loadExistingEventForUpdate(eventId);
            if (ev.getStatus() == EventStatus.SUCCEEDED) {
                // Idempotent completion: no further work needed.
                log.info("event already processed, eventId: {}, type: {}", eventId, eventType);
                return true;
            }

            if (ev.getStatus() == EventStatus.DEAD) {
                return handleDeadEvent(ctx, ev, eventId, eventType);
            }

            if (retryPolicy.exhausted(ev.getAttempts())) {
                return handleRetryExhausted(ctx, ev, eventId, eventType);
            }

            if (isProcessingLeaseStillValid(ev)) {
                log.warn("event still processing by another worker: {}", eventId);
                throw new BizException(ErrorCode.EVENT_PROCESSING);
            }

            // FAILED or stale PROCESSING can be safely re-taken.
            int i = businessEventService.markEventStatusFromFailToProcessing(
                    eventId,
                    eventType,
                    EventStatus.PROCESSING,
                    ev.getAttempts()
            );
            ctx.setAttempts(ev.getAttempts());

            return i != 1;


        }
    }

    public void markSuccess(WebhookContext ctx) {
        businessEventService.markSuccessAndIncrementAttempt(
                ctx.getEventId(),
                EventStatus.SUCCEEDED,
                null
        );
    }

    /**
     * Persist a handler outcome using the attempt snapshot captured during reservation.
     */
    public void markByDecision(WebhookContext ctx, WebhookDecision decision, String errMsg) {
        int attemptInc = decision.eventStatus() == EventStatus.PROCESSING ? 0 : 1;
        businessEventService.markEventStatusWithAttempt(
                ctx.getEventId(),
                ctx.getEventType(),
                decision.eventStatus(),
                errMsg,
                ctx.getAttempts(),
                attemptInc
        );
    }

    private String resolveFallbackOrderNo(WebhookContext ctx) {
        if (ctx == null || ctx.getTrackingId() == null || ctx.getTrackingId().isBlank()) {
            return null;
        }
        if (ctx.getEventType() == null) {
            return null;
        }
        String eventTypeName = ctx.getEventType().name();
        if (eventTypeName.startsWith("ORDER_")
                || eventTypeName.startsWith("CHECKOUT_SESSION_")
                || eventTypeName.startsWith("INVOICE_")
                || eventTypeName.startsWith("SUBSCRIPTION_")
                || eventTypeName.startsWith("CUSTOMER_SUBSCRIPTION_")) {
            return ctx.getTrackingId();
        }
        return null;
    }

    private String resolveFallbackSessionId(WebhookContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.getProviderTrackingId() != null && !ctx.getProviderTrackingId().isBlank()) {
            return ctx.getProviderTrackingId();
        }
        if (ctx.getEventType() != null
                && (ctx.getEventType().name().startsWith("ORDER_")
                || ctx.getEventType().name().startsWith("CHECKOUT_SESSION_"))
                && ctx.getTrackingId() != null
                && !ctx.getTrackingId().isBlank()) {
            return ctx.getTrackingId();
        }
        return null;
    }

    private BusinessEvent loadExistingEventForUpdate(String eventId) {
        try {
            // Event already exists: lock and inspect current state.
            return businessEventService.findByIdOrThrow(eventId);
        } catch (Exception notFoundAfterConflict) {
            // Visibility race after duplicate key; retry later.
            throw new BizException(ErrorCode.EVENT_NOT_FOUND);
        }
    }


    private boolean handleDeadEvent(
            WebhookContext ctx,
            BusinessEvent ev,
            String eventId,
            BusinessEventType eventType
    ) {
        log.info("event already dead, need alarm or reconcile, eventId: {}, type: {}", eventId, eventType);
        // Already terminal locally; keep abnormal trace current.
        if (shouldEscalateToAbnormal(ctx)) {
            abnormalOrchestrator.upsertAbnormalOrder(
                    resolveFallbackOrderNo(ctx),
                    eventId,
                    eventType,
                    resolveFallbackSessionId(ctx),
                    "event_already_dead",
                    "event already dead",
                    AbnormalOrderType.EVENT_RETRY_BUDGET_EXHAUSTED,
                    ctx.getProvider(),
                    null
            );
        }
        paymentFailureEscalationService.recordDeadEventIncident(
                ctx,
                "event_already_dead",
                "event already dead"
        );
        ctx.setAbnormalAlreadyUpserted(true);
        ctx.setAttempts(ev.getAttempts());
        return true;
    }

    private boolean handleRetryExhausted(
            WebhookContext ctx,
            BusinessEvent ev,
            String eventId,
            BusinessEventType eventType
    ) {
        // Retry budget exhausted: move to DEAD and hand over to abnormal queue.
        businessEventService.markEventStatusToDeadWithAttempt(
                eventId,
                eventType,
                EventStatus.DEAD,
                "max attempts exceeded",
                ev.getAttempts(),
                0
        );
        if (shouldEscalateToAbnormal(ctx)) {
            abnormalOrchestrator.upsertAbnormalOrder(
                    resolveFallbackOrderNo(ctx),
                    eventId,
                    eventType,
                    resolveFallbackSessionId(ctx),
                    "handleStripeEvent_max_attempts",
                    "max attempts exceeded",
                    AbnormalOrderType.EVENT_RETRY_BUDGET_EXHAUSTED,
                    ctx.getProvider(),
                    null
            );
        }
        paymentFailureEscalationService.recordRetryExhaustedIncident(
                ctx,
                "handleStripeEvent_max_attempts",
                "max attempts exceeded"
        );
        ctx.setAbnormalAlreadyUpserted(true);

        log.error(
                "event already processed, eventId: {}, type: {}, but failed 5 times, will not retry",
                eventId,
                eventType
        );
        return true;
    }

    private boolean isProcessingLeaseStillValid(BusinessEvent ev) {
        // Fresh PROCESSING lease means another worker still owns this event.
        Instant handledAt = ProviderTimeMapper.toInstant(ev.getEventHandledAt());
        return ev.getStatus() == EventStatus.PROCESSING
                && handledAt != null
                && Instant.now().isBefore(retryPolicy.mainStreamNextRetryAt(ev.getAttempts(), handledAt));
    }

    private boolean shouldEscalateToAbnormal(WebhookContext ctx) {
        String trackingId = resolveFallbackOrderNo(ctx);
        String providerTrackingId = resolveFallbackSessionId(ctx);
        return trackingId != null && providerTrackingId != null;
    }
}
