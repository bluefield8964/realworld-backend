package realworld_backend.commerce.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.model.log.AbnormalDomainType;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.model.log.WebhookIncident;
import realworld_backend.commerce.model.log.WebhookIncidentType;
import realworld_backend.commerce.service.core.PaymentChannelException;
import realworld_backend.commerce.service.core.WebhookContext;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFailureEscalationService {
    private static final int PRE_BUSINESS_BURST_THRESHOLD = 5;
    private static final Duration PRE_BUSINESS_BURST_WINDOW = Duration.ofMinutes(10);
    private static final Duration PRE_BUSINESS_STUCK_DURATION = Duration.ofMinutes(30);
    private static final int BUSINESS_READY_BURST_THRESHOLD = 3;
    private static final Duration BUSINESS_READY_BURST_WINDOW = Duration.ofMinutes(5);

    private final WebhookIncidentOrchestrator webhookIncidentOrchestrator;
    private final AbnormalOrchestrator abnormalOrchestrator;

    public boolean shouldUpsertAbnormal(WebhookContext ctx, Throwable ex) {
        if (!hasReconcileKey(ctx)) {
            return false;
        }
        if (ex instanceof PaymentChannelException channelException) {
            return !channelException.isRetryable();
        }
        if (ex instanceof BizException bizException) {
            ErrorCode code = bizException.getErrorCode();
            return code == ErrorCode.ORDER_NOT_FOUND
                    || code == ErrorCode.PAYMENT_NOT_FOUND
                    || code == ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND
                    || code == ErrorCode.SUBSCRIPTION_HISTORY_NOT_FOUND
                    || code == ErrorCode.RETRY_EXHAUSTED;
        }
        return false;
    }

    public boolean shouldRecordIncident(WebhookContext ctx, Throwable ex) {
        WebhookIncidentType incidentType = resolveIncidentType(ctx, ex);
        if (ex instanceof BizException bizException) {
            ErrorCode code = bizException.getErrorCode();
            if (code == ErrorCode.LOCK_CANNOT_ACQUIRE
                    || code == ErrorCode.LOCK_INTERRUPTED
                    || code == ErrorCode.EVENT_PROCESSING
                    || code == ErrorCode.IDEMPOTENCY_LOCK_FAILED) {
                return false;
            }
            if (!isTransientPayloadOrRaceError(code)) {
                return true;
            }
            return isNearRetryExhausted(ctx) || reachedBusinessReadyBurstThreshold(ctx, incidentType);
        }
        if (ex instanceof PaymentChannelException channelException) {
            if (!channelException.isRetryable()) {
                return true;
            }
            return !isPreBusiness(ctx) && reachedBusinessReadyBurstThreshold(ctx, incidentType);
        }
        return true;
    }

    public void recordIncidentForException(
            WebhookContext ctx,
            Throwable ex,
            String requestId,
            String reason,
            String errorMessage,
            String rawPayload
    ) {
        if (!shouldRecordIncident(ctx, ex)) {
            return;
        }
        try {
            String provider = ctx != null ? ctx.getProvider() : null;
            String eventId = ctx != null ? ctx.getEventId() : null;
            String eventType = ctx != null && ctx.getEventType() != null ? ctx.getEventType().toString() : null;
            String trackingId = ctx != null ? ctx.getTrackingId() : null;
            String providerTrackingId = ctx != null ? ctx.getProviderTrackingId() : null;

            WebhookIncident incident = webhookIncidentOrchestrator.recordIncident(
                    provider,
                    resolveDomainType(ctx),
                    eventId,
                    eventType,
                    requestId,
                    trackingId,
                    providerTrackingId,
                    resolveIncidentType(ctx, ex),
                    reason,
                    errorMessage,
                    rawPayload
            );

            maybeEscalatePreBusinessStuck(ctx, ex, incident);
        } catch (Exception incidentWriteError) {
            // Incident write failure must not block state progression.
            log.error("recordIncidentForException failed but main flow continues: {}", incidentWriteError.getMessage(), incidentWriteError);
        }
    }

    public void recordDeadEventIncident(WebhookContext ctx, String reason, String errorMessage) {
        try {
            webhookIncidentOrchestrator.recordIncident(
                    ctx.getProvider(),
                    resolveDomainType(ctx),
                    ctx.getEventId(),
                    ctx.getEventType() == null ? null : ctx.getEventType().toString(),
                    null,
                    ctx.getTrackingId(),
                    ctx.getProviderTrackingId(),
                    WebhookIncidentType.EVENT_ALREADY_DEAD,
                    reason,
                    errorMessage,
                    ctx.getProviderRawEvent() == null ? null : ctx.getProviderRawEvent().getRawObjectJson()
            );
        } catch (Exception incidentWriteError) {
            log.error("recordDeadEventIncident failed but main flow continues: {}", incidentWriteError.getMessage(), incidentWriteError);
        }
    }

    public void recordRetryExhaustedIncident(WebhookContext ctx, String reason, String errorMessage) {
        try {
            webhookIncidentOrchestrator.recordIncident(
                    ctx.getProvider(),
                    resolveDomainType(ctx),
                    ctx.getEventId(),
                    ctx.getEventType() == null ? null : ctx.getEventType().toString(),
                    null,
                    ctx.getTrackingId(),
                    ctx.getProviderTrackingId(),
                    WebhookIncidentType.EVENT_RETRY_BUDGET_EXHAUSTED,
                    reason,
                    errorMessage,
                    ctx.getProviderRawEvent() == null ? null : ctx.getProviderRawEvent().getRawObjectJson()
            );
        } catch (Exception incidentWriteError) {
            log.error("recordRetryExhaustedIncident failed but main flow continues: {}", incidentWriteError.getMessage(), incidentWriteError);
        }
    }

    public AbnormalOrderType resolveAbnormalType(Throwable ex) {
        if (ex instanceof PaymentChannelException channelException) {
            return channelException.isRetryable() ? null : AbnormalOrderType.PROVIDER_TERMINAL_FAILURE;
        }
        if (ex instanceof BizException bizException) {
            ErrorCode code = bizException.getErrorCode();
            if (code == ErrorCode.ORDER_NOT_FOUND) {
                return AbnormalOrderType.ORDER_MISSING;
            }
            if (code == ErrorCode.PAYMENT_NOT_FOUND) {
                return AbnormalOrderType.PAYMENT_MISSING;
            }
            if (code == ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND
                    || code == ErrorCode.SUBSCRIPTION_HISTORY_NOT_FOUND) {
                return AbnormalOrderType.SUBSCRIPTION_MISSING;
            }
            if (code == ErrorCode.RETRY_EXHAUSTED) {
                return AbnormalOrderType.EVENT_RETRY_BUDGET_EXHAUSTED;
            }
        }
        return null;
    }

    private WebhookIncidentType resolveIncidentType(WebhookContext ctx, Throwable ex) {
        if (ex instanceof PaymentChannelException channelException) {
            if (isPreBusiness(ctx)) {
                return WebhookIncidentType.PRE_BUSINESS_PARSE_FAILED;
            }
            return channelException.isRetryable()
                    ? WebhookIncidentType.UNCLASSIFIED_SYSTEM_EXCEPTION
                    : WebhookIncidentType.PROVIDER_TERMINAL_FAILURE;
        }
        if (ex instanceof BizException bizException) {
            ErrorCode code = bizException.getErrorCode();
            if (code == ErrorCode.JSON_ERROR
                    || code == ErrorCode.STRIPE_SESSION_NOT_FOUND
                    || code == ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE
                    || code == ErrorCode.INVOICE_SESSION_NOT_FOUND
                    || code == ErrorCode.SUBSCRIPTION_SESSION_NOT_FOUND) {
                return WebhookIncidentType.PAYLOAD_PARSE_FAILED;
            }
            if (code == ErrorCode.SUBSCRIPTION_HISTORY_NOT_FOUND) {
                return WebhookIncidentType.SUBSCRIPTION_HISTORY_MISSING;
            }
        }
        return WebhookIncidentType.UNCLASSIFIED_SYSTEM_EXCEPTION;
    }

    private AbnormalDomainType resolveDomainType(WebhookContext ctx) {
        if (ctx == null || ctx.getEventType() == null) {
            return AbnormalDomainType.SYSTEM;
        }
        String name = ctx.getEventType().name();
        if (name.startsWith("ORDER_") || name.startsWith("CHECKOUT_SESSION_")) {
            return AbnormalDomainType.ORDER;
        }
        if (name.startsWith("SUBSCRIPTION_")
                || name.startsWith("CUSTOMER_SUBSCRIPTION_")) {
            return AbnormalDomainType.SUBSCRIPTION;
        }
        if (name.startsWith("INVOICE_")) {
            return AbnormalDomainType.INVOICE;
        }
        return AbnormalDomainType.SYSTEM;
    }



    private boolean isNearRetryExhausted(WebhookContext ctx) {
        if (ctx == null) {
            return false;
        }
        return ctx.getAttempts() >= 4;
    }

    private boolean isPreBusiness(WebhookContext ctx) {
        return !hasReconcileKey(ctx);
    }

    private boolean hasReconcileKey(WebhookContext ctx) {
        if (ctx == null) {
            return false;
        }
        if (hasText(ctx.getTrackingId())) {
            return true;
        }
        return hasText(ctx.getProviderTrackingId());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void maybeEscalatePreBusinessStuck(WebhookContext ctx, Throwable ex, WebhookIncident incident) {
        if (!isPreBusiness(ctx) || incident == null) {
            return;
        }
        if (!isEscalationCandidate(ex)) {
            return;
        }
        if (!reachedPreBusinessStuckThreshold(incident)) {
            return;
        }

        String syntheticTracking = "PREBUSINESS:" + incident.getDedupeKey();
        String provider = incident.getProvider() == null ? "UNKNOWN_PROVIDER" : incident.getProvider();
        try {
            abnormalOrchestrator.upsertAbnormalOrder(
                    null,
                    incident.getEventId(),
                    null,
                    syntheticTracking,
                    "pre_business_stuck_threshold_exceeded",
                    incident.getErrorMessage(),
                    AbnormalOrderType.PRE_BUSINESS_STUCK,
                    provider,
                    incident.getRequestId()
            );
        } catch (Exception abnormalWriteError) {
            // Keep the failure observable and let webhook retries attempt again.
            log.error("Failed to upsert pre-business stuck abnormal, incidentId={}", incident.getId(), abnormalWriteError);
            throw abnormalWriteError;
        }
    }

    private boolean isEscalationCandidate(Throwable ex) {
        if (ex instanceof PaymentChannelException) {
            return true;
        }
        if (ex instanceof BizException bizException) {
            ErrorCode code = bizException.getErrorCode();
            return isTransientPayloadOrRaceError(code)
                    || code == ErrorCode.STRIPE_SESSION_NOT_FOUND
                    || code == ErrorCode.SUBSCRIPTION_SESSION_NOT_FOUND
                    || code == ErrorCode.INVOICE_SESSION_NOT_FOUND;
        }
        return true;
    }
    private boolean isTransientPayloadOrRaceError(ErrorCode code) {
        return code == ErrorCode.JSON_ERROR
                || code == ErrorCode.STRIPE_SESSION_NOT_FOUND
                || code == ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE
                || code == ErrorCode.EVENT_NOT_FOUND;
    }
    private boolean reachedPreBusinessStuckThreshold(WebhookIncident incident) {
        LocalDateTime first = incident.getFirstOccurredAt();
        LocalDateTime last = incident.getLastOccurredAt();
        if (first == null || last == null) {
            return false;
        }
        Duration duration = Duration.between(first, last);
        boolean burstExceeded = incident.getOccurrenceCount() >= PRE_BUSINESS_BURST_THRESHOLD
                && duration.compareTo(PRE_BUSINESS_BURST_WINDOW) <= 0;
        boolean stuckExceeded = duration.compareTo(PRE_BUSINESS_STUCK_DURATION) >= 0;
        return burstExceeded || stuckExceeded;
    }

    private boolean reachedBusinessReadyBurstThreshold(WebhookContext ctx, WebhookIncidentType incidentType) {
        if (ctx == null || isPreBusiness(ctx) || incidentType == null) {
            return false;
        }
        try {
            return webhookIncidentOrchestrator.findIncidentSnapshot(
                            ctx.getProvider(),
                            incidentType,
                            ctx.getEventId(),
                            null,
                            ctx.getProviderRawEvent() == null ? null : ctx.getProviderRawEvent().getRawObjectJson()
                    )
                    .map(this::isBusinessReadyBurstExceeded)
                    .orElse(false);
        } catch (Exception queryError) {
            log.warn("business-ready burst query failed, fallback to near-exhausted only: {}", queryError.getMessage());
            return false;
        }
    }

    private boolean isBusinessReadyBurstExceeded(WebhookIncident incident) {
        LocalDateTime first = incident.getFirstOccurredAt();
        LocalDateTime last = incident.getLastOccurredAt();
        if (first == null || last == null) {
            return false;
        }
        Duration duration = Duration.between(first, last);
        return incident.getOccurrenceCount() >= BUSINESS_READY_BURST_THRESHOLD
                && duration.compareTo(BUSINESS_READY_BURST_WINDOW) <= 0;
    }
}
