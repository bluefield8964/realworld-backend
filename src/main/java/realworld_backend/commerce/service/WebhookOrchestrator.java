package realworld_backend.commerce.service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.service.core.*;
import realworld_backend.commerce.service.impl.handler.WebhookHandlerRouter;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class WebhookOrchestrator {

    private final PaymentChannelRouter paymentChannelRouter;
    private final WebhookHandlerRouter handlerRouter;
    private final EventReservationService reservationService;
    private final WebhookErrorPolicy webhookErrorPolicy;
    private final AbnormalOrchestrator abnormalOrchestrator;
    private final PaymentFailureEscalationService paymentFailureEscalationService;

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
        try {
            ProviderRawEvent rawEvent = parseRawEvent(payload, sigHeader, secret, provider);
            if (isUnknownEvent(rawEvent.getType())) {
                return new WebhookDecision(true, null, org.springframework.http.HttpStatus.OK, false, null);
            }

            ctx = buildWebhookContext(provider, rawEvent);
            if (shouldShortCircuitByReservation(ctx)) {
                return new WebhookDecision(true, null, org.springframework.http.HttpStatus.OK, false, null);
            }

            WebhookHandler handler = handlerRouter.get(rawEvent.getType());
            // No registered handler: accept to avoid endless provider retries.
            if (handler == null) {
                return new WebhookDecision(true, null, org.springframework.http.HttpStatus.OK, false, null);
            }

            // Execute type-specific business logic.
            handler.handle(ctx);

            // Mark event as succeeded after business handler completes.
            reservationService.markSuccess(ctx);
            return new WebhookDecision(true, null, org.springframework.http.HttpStatus.OK, false, null);

        } catch (PaymentChannelException e) {
            return handlePaymentChannelException(ctx, e, payload);
        } catch (Exception ex) {
            return handleUnexpectedException(ctx, ex, payload);
        }
    }

    private ProviderRawEvent parseRawEvent(String payload, String sigHeader, String secret, String provider) {
        PaymentChannel channel = paymentChannelRouter.get(provider);
        // Parse raw webhook to provider-neutral event model.
        return channel.parseRawEvent(payload, sigHeader, secret);
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

    private boolean shouldShortCircuitByReservation(WebhookContext ctx) {
        // Reserve event processing ownership or short-circuit if already completed.
        return reservationService.reserveOrTakeover(ctx);
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
