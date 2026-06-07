package realworld_backend.commerce.service.webhook;


import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.model.exception.WebhookDuplicateIgnoredException;
import realworld_backend.commerce.service.AbnormalOrchestrator;
import realworld_backend.commerce.service.core.PaymentChannel;
import realworld_backend.commerce.service.core.PaymentChannelException;
import realworld_backend.commerce.service.core.PaymentChannelRouter;
import realworld_backend.commerce.service.metrics.CommerceMetricsService;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.webhook.core.WebhookDecision;
import realworld_backend.commerce.service.webhook.core.WebhookErrorPolicy;
import realworld_backend.commerce.service.webhook.core.WebhookHandler;
import realworld_backend.commerce.service.webhook.handler.WebhookHandlerRouter;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookOrchestrator {

    private final PaymentChannelRouter paymentChannelRouter;
    private final WebhookHandlerRouter handlerRouter;
    private final EventReservationService reservationService;
    private final WebhookErrorPolicy webhookErrorPolicy;
    private final AbnormalOrchestrator abnormalOrchestrator;
    private final PaymentFailureEscalationService paymentFailureEscalationService;
    private final CommerceMetricsService commerceMetricsService;

    /**
     * Orchestrates webhook processing as a deterministic pipeline:
     * 1) parse/normalize provider payload
     * 2) filter unsupported events
     * 3) reserve or takeover event ownership (idempotency)
     * 4) dispatch business handler by event type
     * 5) persist success/failure event status
     * 6) apply fallback abnormal-order policy when needed
     */
    public WebhookDecision process(String payload, String sigHeader, String secret, String provider) {
        WebhookContext ctx = null;
        Timer.Sample timerSample = commerceMetricsService.startTimer();
        String family = "unknown";
        String result = "unknown";
        try {
            log.info("webhook process started, provider={}, payloadLength={}, signaturePresent={}",
                    provider,
                    payload == null ? 0 : payload.length(),
                    sigHeader != null && !sigHeader.isBlank());
            ProviderRawEvent rawEvent = parseRawEvent(payload, sigHeader, secret, provider);
            family = commerceMetricsService.familyOf(rawEvent.getType());
            log.info("webhook raw event parsed, provider={}, family={}, eventType={}, eventId={}",
                    provider,
                    family,
                    rawEvent.getType(),
                    rawEvent.getEventId());
            commerceMetricsService.recordWebhookReceived(
                    provider,
                    family,
                    commerceMetricsService.eventOf(rawEvent.getType())
            );
            if (isUnknownEvent(rawEvent.getType())) {
                result = "ignored_unknown_event";
                log.info("webhook ignored unknown event, provider={}, eventId={}, eventType={}",
                        provider, rawEvent.getEventId(), rawEvent.getType());
                return new WebhookDecision(true, null, org.springframework.http.HttpStatus.OK, false, null);
            }

            ctx = buildWebhookContext(provider, rawEvent);
            log.info("webhook context built, provider={}, eventId={}, eventType={}, trackingId={}, providerTrackingId={}",
                    ctx.getProvider(),
                    ctx.getEventId(),
                    ctx.getEventType(),
                    ctx.getTrackingId(),
                    ctx.getProviderTrackingId());
            if (reservationService.reserveOrTakeover(ctx)) {
                result = "duplicate_takeover";
                log.info("webhook duplicate/takeover skipped, provider={}, eventId={}, eventType={}",
                        provider, ctx.getEventId(), ctx.getEventType());
                return new WebhookDecision(true, null, org.springframework.http.HttpStatus.OK, false, null);
            }

            WebhookHandler handler = handlerRouter.get(rawEvent.getType());
            // No registered handler: accept to avoid endless provider retries.
            if (handler == null) {
                reservationService.markSuccess(ctx);
                result = "ignored_no_handler";
                log.info("webhook no handler registered, provider={}, eventId={}, eventType={}",
                        provider, ctx.getEventId(), ctx.getEventType());
                return new WebhookDecision(true, null, org.springframework.http.HttpStatus.OK, false, null);
            }

            log.info("webhook handler resolved, provider={}, eventId={}, eventType={}, handler={}",
                    provider,
                    ctx.getEventId(),
                    ctx.getEventType(),
                    handler.getClass().getSimpleName());
            // Execute type-specific business logic.
            handler.handle(ctx);

            // Mark event as succeeded after business handler completes.
            reservationService.markSuccess(ctx);
            result = "succeeded";
            log.info("webhook business handler succeeded, provider={}, eventId={}, eventType={}, handler={}",
                    provider,
                    ctx.getEventId(),
                    ctx.getEventType(),
                    handler.getClass().getSimpleName());
            return new WebhookDecision(true, null, org.springframework.http.HttpStatus.OK, false, null);

        } catch (PaymentChannelException e) {
            result = "payment_channel_exception";
            log.warn("webhook payment channel exception, provider={}, eventId={}, eventType={}, requestId={}, retryable={}, message={}",
                    provider,
                    ctx == null ? null : ctx.getEventId(),
                    ctx == null ? null : ctx.getEventType(),
                    e.getRequestId(),
                    e.isRetryable(),
                    e.getMessage());
            return handlePaymentChannelException(ctx, e, payload);
        } catch (WebhookDuplicateIgnoredException ex) {
            result = "ignored_duplicate";
            log.info("webhook duplicate ignored, provider={}, eventId={}, eventType={}, message={}",
                    provider,
                    ctx == null ? null : ctx.getEventId(),
                    ctx == null ? null : ctx.getEventType(),
                    ex.getMessage());
            return webhookErrorPolicy.classify(ex);
        } catch
        (Exception ex) {
            WebhookDecision decision = webhookErrorPolicy.classify(ex);
            result = decision.terminal() ? "terminal_failure" : "retryable_failure";
            log.error("webhook unexpected exception, provider={}, eventId={}, eventType={}, terminal={}, httpStatus={}, message={}",
                    provider,
                    ctx == null ? null : ctx.getEventId(),
                    ctx == null ? null : ctx.getEventType(),
                    decision.terminal(),
                    decision.httpStatus(),
                    ex.getMessage(),
                    ex);
            return handleUnexpectedException(ctx, ex, payload);
        } finally {
            commerceMetricsService.recordWebhookResult(provider, family, result);
            commerceMetricsService.recordWebhookDuration(timerSample, provider, family, result);
            log.info("webhook process finished, provider={}, family={}, result={}", provider, family, result);
        }
    }

    public WebhookDecision processReplay(String payload, ProviderRawEvent rawEvent, String provider) {
        return processParsedRawEvent(payload, rawEvent, provider, "replay");
    }

    private ProviderRawEvent parseRawEvent(String payload, String sigHeader, String secret, String provider) {
        PaymentChannel channel = paymentChannelRouter.get(provider);
        // Parse raw webhook to provider-neutral event model.
        return channel.parseRawEvent(payload, sigHeader, secret);
    }

    private WebhookDecision processParsedRawEvent(
            String payload,
            ProviderRawEvent rawEvent,
            String provider,
            String source
    ) {
        WebhookContext ctx = null;
        Timer.Sample timerSample = commerceMetricsService.startTimer();
        String family = "unknown";
        String result = "unknown";
        try {
            log.info("webhook process started, provider={}, source={}, payloadLength={}, eventId={}, eventType={}",
                    provider,
                    source,
                    payload == null ? 0 : payload.length(),
                    rawEvent == null ? null : rawEvent.getEventId(),
                    rawEvent == null ? null : rawEvent.getType());
            if (rawEvent == null) {
                throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
            }

            family = commerceMetricsService.familyOf(rawEvent.getType());
            log.info("webhook raw event parsed, provider={}, source={}, family={}, eventType={}, eventId={}",
                    provider,
                    source,
                    family,
                    rawEvent.getType(),
                    rawEvent.getEventId());
            commerceMetricsService.recordWebhookReceived(
                    provider,
                    family,
                    commerceMetricsService.eventOf(rawEvent.getType())
            );
            if (isUnknownEvent(rawEvent.getType())) {
                result = "ignored_unknown_event";
                log.info("webhook ignored unknown event, provider={}, source={}, eventId={}, eventType={}",
                        provider, source, rawEvent.getEventId(), rawEvent.getType());
                return new WebhookDecision(true, null, org.springframework.http.HttpStatus.OK, false, null);
            }

            ctx = buildWebhookContext(provider, rawEvent);
            log.info("webhook context built, provider={}, source={}, eventId={}, eventType={}, trackingId={}, providerTrackingId={}",
                    ctx.getProvider(),
                    source,
                    ctx.getEventId(),
                    ctx.getEventType(),
                    ctx.getTrackingId(),
                    ctx.getProviderTrackingId());
            if (reservationService.reserveOrTakeover(ctx)) {
                result = "duplicate_takeover";
                log.info("webhook duplicate/takeover skipped, provider={}, source={}, eventId={}, eventType={}",
                        provider, source, ctx.getEventId(), ctx.getEventType());
                return new WebhookDecision(true, null, org.springframework.http.HttpStatus.OK, false, null);
            }

            WebhookHandler handler = handlerRouter.get(rawEvent.getType());
            if (handler == null) {
                reservationService.markSuccess(ctx);
                result = "ignored_no_handler";
                log.info("webhook no handler registered, provider={}, source={}, eventId={}, eventType={}",
                        provider, source, ctx.getEventId(), ctx.getEventType());
                return new WebhookDecision(true, null, org.springframework.http.HttpStatus.OK, false, null);
            }

            log.info("webhook handler resolved, provider={}, source={}, eventId={}, eventType={}, handler={}",
                    provider,
                    source,
                    ctx.getEventId(),
                    ctx.getEventType(),
                    handler.getClass().getSimpleName());
            handler.handle(ctx);
            reservationService.markSuccess(ctx);
            result = "succeeded";
            log.info("webhook business handler succeeded, provider={}, source={}, eventId={}, eventType={}, handler={}",
                    provider,
                    source,
                    ctx.getEventId(),
                    ctx.getEventType(),
                    handler.getClass().getSimpleName());
            return new WebhookDecision(true, null, org.springframework.http.HttpStatus.OK, false, null);

        } catch (PaymentChannelException e) {
            result = "payment_channel_exception";
            log.warn("webhook payment channel exception, provider={}, source={}, eventId={}, eventType={}, requestId={}, retryable={}, message={}",
                    provider,
                    source,
                    ctx == null ? null : ctx.getEventId(),
                    ctx == null ? null : ctx.getEventType(),
                    e.getRequestId(),
                    e.isRetryable(),
                    e.getMessage());
            return handlePaymentChannelException(ctx, e, payload);
        } catch (WebhookDuplicateIgnoredException ex) {
            result = "ignored_duplicate";
            log.info("webhook duplicate ignored, provider={}, source={}, eventId={}, eventType={}, message={}",
                    provider,
                    source,
                    ctx == null ? null : ctx.getEventId(),
                    ctx == null ? null : ctx.getEventType(),
                    ex.getMessage());
            return webhookErrorPolicy.classify(ex);
        } catch (Exception ex) {
            WebhookDecision decision = webhookErrorPolicy.classify(ex);
            result = decision.terminal() ? "terminal_failure" : "retryable_failure";
            log.error("webhook unexpected exception, provider={}, source={}, eventId={}, eventType={}, terminal={}, httpStatus={}, message={}",
                    provider,
                    source,
                    ctx == null ? null : ctx.getEventId(),
                    ctx == null ? null : ctx.getEventType(),
                    decision.terminal(),
                    decision.httpStatus(),
                    ex.getMessage(),
                    ex);
            return handleUnexpectedException(ctx, ex, payload);
        } finally {
            commerceMetricsService.recordWebhookResult(provider, family, result);
            commerceMetricsService.recordWebhookDuration(timerSample, provider, family, result);
            log.info("webhook process finished, provider={}, source={}, family={}, result={}", provider, source, family, result);
        }
    }

    private boolean isUnknownEvent(BusinessEventType type) {
        // Unknown/non-actionable events are accepted and ignored.
        return type == null || type == BusinessEventType.UNKNOWN_EVENT;
    }

    private WebhookContext buildWebhookContext(String provider, ProviderRawEvent rawEvent) {
        return WebhookContext.builder()
                .provider(provider)
                .eventId(rawEvent.getEventId())
                .eventType(rawEvent.getType())
                .trackingId(null)
                .providerRawEvent(rawEvent)
                .abnormalAlreadyUpserted(false)
                .build();
    }

    private WebhookDecision handlePaymentChannelException(
            WebhookContext ctx,
            PaymentChannelException e,
            String payload
    ) {
        // Provider parse/verification/channel errors are classified without event context.
        WebhookDecision decision = webhookErrorPolicy.classify(e);
        paymentFailureEscalationService.recordIncidentForException(
                ctx,
                e,
                e.getRequestId(),
                "payment_channel_exception",
                e.getMessage(),
                payload
        );
        return decision;
    }

    private WebhookDecision handleUnexpectedException(WebhookContext ctx, Exception ex, String payload) {
        // Any business/runtime exception is normalized by policy.
        WebhookDecision decision = webhookErrorPolicy.classify(ex);

        // Persist event failure state when policy provides a StripeEvent status.
        if (shouldPersistEventDecision(ctx, ex, decision)) {
            reservationService.markByDecision(ctx, decision, ex.getMessage());
        }

        // Fallback abnormal upsert is skipped if an inner branch already wrote one.
        if (shouldUpsertFallbackAbnormal(ctx, ex)) {
            abnormalOrchestrator.upsertAbnormalOrder(
                    resolveFallbackOrderNo(ctx),
                    ctx.getEventId(),
                    ctx.getEventType(),
                    resolveFallbackSessionId(ctx),
                    "webhook_policy",
                    ex.getMessage(),
                    paymentFailureEscalationService.resolveAbnormalType(ex),
                    ctx.getProvider(),
                    null
            );
        }
        paymentFailureEscalationService.recordIncidentForException(
                ctx,
                ex,
                null,
                "webhook_policy",
                ex.getMessage(),
                payload
        );

        return decision;
    }

    private boolean shouldPersistEventDecision(WebhookContext ctx, Exception ex, WebhookDecision decision) {
        return ctx != null
                && ctx.getEventId() != null
                && decision.eventStatus() != null
                && !(ex instanceof BizException biz && biz.getErrorCode() == ErrorCode.EVENT_PROCESSING);
    }

    private boolean shouldUpsertFallbackAbnormal(WebhookContext ctx, Exception ex) {
        return ctx != null
                && !ctx.isAbnormalAlreadyUpserted()
                && paymentFailureEscalationService.shouldUpsertAbnormal(ctx, ex);
    }

    private String resolveFallbackSessionId(WebhookContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.getProviderTrackingId() != null && !ctx.getProviderTrackingId().isBlank()) {
            return ctx.getProviderTrackingId();
        }
        return null;
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
}
