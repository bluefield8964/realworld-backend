package realworld_backend.service.CommerceService.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import realworld_backend.dto.Exception.BizException;
import realworld_backend.dto.Exception.ErrorCode;
import realworld_backend.model.commerceModule.AbnormalOrderStatus;
import realworld_backend.model.commerceModule.StripeEventStatus;
import realworld_backend.service.CommerceService.core.PaymentChannelException;
import realworld_backend.service.CommerceService.core.WebhookDecision;
import realworld_backend.service.CommerceService.core.WebhookErrorPolicy;

import java.util.Set;

@Component
public class DefaultWebhookErrorPolicy implements WebhookErrorPolicy {
    private final int MAX_ATTEMPTS = 5;



    @Override
    public WebhookDecision classify(Throwable ex) {
        if (ex instanceof BizException biz) {
            ErrorCode code = biz.getErrorCode();

            if (code == ErrorCode.ORDER_NOT_FOUND) {
                return new WebhookDecision(true, StripeEventStatus.DEAD,
                        HttpStatus.OK, true, AbnormalOrderStatus.ORDER_MISSING);
            }
            if (code == ErrorCode.PAYMENT_NOT_FOUND) {
                return new WebhookDecision(true, StripeEventStatus.DEAD,
                        HttpStatus.OK, true, AbnormalOrderStatus.PAYMENT_MISSING);
            }
            if (code == ErrorCode.TRIPE_SESSION_NOT_FOUND
                    || code == ErrorCode.JSON_ERROR
                    || code == ErrorCode.RETRY_EXHAUSTED
                    || code == ErrorCode.ORDER_FAILED
            ) {
                return new WebhookDecision(true, StripeEventStatus.DEAD,
                        HttpStatus.OK, true, AbnormalOrderStatus.RETRY_EXHAUSTED);
            }
            if (code == ErrorCode.IDEMPOTENCYLOCK_CANT_REQUIRE) {
                return new WebhookDecision(true, StripeEventStatus.PROCESSING,
                        HttpStatus.OK, false, null);
            }
            // recoverable BizException (lock contention / event processing / etc.)
            return new WebhookDecision(false, StripeEventStatus.FAILED, HttpStatus.INTERNAL_SERVER_ERROR, false, null);

        } else if (ex instanceof PaymentChannelException) {
            String errorCode = ((PaymentChannelException) ex).getErrorCode();
            Set<String> recallableErrors = Set.of(
                    // Temporary errors
                    "processing_error",
                    "temporary_failure",
                    "try_again_later",
                    "api_connection_error",
                    "api_error",
                    "service_unavailable",
                    "timeout_error",

                    // Rate limiting (with backoff)
                    "rate_limit",

                    // Processing states
                    "requires_action",
                    "requires_confirmation",
                    "requires_capture",
                    "processing",
                    "open",
                    "requires_payment_method"
            );
            boolean contains = recallableErrors.contains(errorCode);
            if (contains && ((PaymentChannelException) ex).isRetryable()) {
                return new WebhookDecision(false, StripeEventStatus.FAILED, HttpStatus.INTERNAL_SERVER_ERROR, false, null);
            }else {
                return new WebhookDecision(true, StripeEventStatus.DEAD, HttpStatus.OK, true, AbnormalOrderStatus.MANUAL_REVIEW);
            }
        }

        // unknown exception => recoverable first
        return new WebhookDecision(false, StripeEventStatus.FAILED, HttpStatus.INTERNAL_SERVER_ERROR, false, null);
    }


}
