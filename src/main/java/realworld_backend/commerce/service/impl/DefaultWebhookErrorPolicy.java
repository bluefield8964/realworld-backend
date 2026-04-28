package realworld_backend.commerce.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.model.EventStatus;
import realworld_backend.commerce.service.core.PaymentChannelException;
import realworld_backend.commerce.service.core.WebhookDecision;
import realworld_backend.commerce.service.core.WebhookErrorPolicy;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

@Component
public class DefaultWebhookErrorPolicy implements WebhookErrorPolicy {
    private final int MAX_ATTEMPTS = 5;


    @Override
    public WebhookDecision classify(Throwable ex) {
        if (ex instanceof BizException biz) {
            ErrorCode code = biz.getErrorCode();

            if (code == ErrorCode.ORDER_NOT_FOUND) {
                return new WebhookDecision(true, EventStatus.DEAD,
                        HttpStatus.OK, false, null);
            }
            if (code == ErrorCode.PAYMENT_NOT_FOUND) {
                return new WebhookDecision(true, EventStatus.DEAD,
                        HttpStatus.OK, false, null);
            }
            if (code == ErrorCode.RETRY_EXHAUSTED
            ) {
                return new WebhookDecision(true, EventStatus.DEAD,
                        HttpStatus.OK, false, null);
            }
            if (code == ErrorCode.ORDER_FAILED) {
                // Local order is already in terminal failed state; stop provider retries.
                return new WebhookDecision(true, EventStatus.DEAD,
                        HttpStatus.OK, false, null);
            }


            if (code == ErrorCode.STRIPE_SESSION_NOT_FOUND
                    || code == ErrorCode.JSON_ERROR
                    || code == ErrorCode.STATEMENT_DOES_NOT_MATCH_EVENT_TYPE
            ) {
                return new WebhookDecision(false, EventStatus.FAILED,
                        HttpStatus.INTERNAL_SERVER_ERROR, false, null);
            }

            if (code == ErrorCode.LOCK_CANNOT_ACQUIRE) {
                // Lock contention is treated as retryable failure, so attempts can move forward.
                return new WebhookDecision(false, EventStatus.FAILED,
                        HttpStatus.INTERNAL_SERVER_ERROR, false, null);
            }
            if (code == ErrorCode.LOCK_INTERRUPTED) {
                // Interrupted lock wait should also consume retry budget.
                return new WebhookDecision(false, EventStatus.FAILED,
                        HttpStatus.INTERNAL_SERVER_ERROR, false, null);
            }
            if (code == ErrorCode.IDEMPOTENCY_LOCK_FAILED) {
                return new WebhookDecision(false, EventStatus.PROCESSING,
                        HttpStatus.INTERNAL_SERVER_ERROR, false, null);
            }
            if (code == ErrorCode.EVENT_NOT_FOUND) {
                return new WebhookDecision(false, EventStatus.FAILED,
                        HttpStatus.INTERNAL_SERVER_ERROR, false, null);
            }

            if (code == ErrorCode.EVENT_PROCESSING) {
                return new WebhookDecision(false, EventStatus.PROCESSING,
                        HttpStatus.INTERNAL_SERVER_ERROR, false, null);
            }
            // recoverable BizException (lock contention / event processing / etc.)
            return new WebhookDecision(false, EventStatus.FAILED, HttpStatus.INTERNAL_SERVER_ERROR, false, null);

        } else if (ex instanceof PaymentChannelException channelException) {
            // Channel adapter already mapped provider-specific error semantics.
            if (channelException.isRetryable()) {
                return new WebhookDecision(false, EventStatus.FAILED, HttpStatus.INTERNAL_SERVER_ERROR, false, null);
            } else {
                return new WebhookDecision(true, EventStatus.DEAD, HttpStatus.OK, true, null);
            }
        }

        // unknown exception => recoverable first
        return new WebhookDecision(false, EventStatus.FAILED, HttpStatus.INTERNAL_SERVER_ERROR, false, null);
    }


}

