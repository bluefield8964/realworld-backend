package realworld_backend.commerce.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import realworld_backend.commerce.model.EventStatus;
import realworld_backend.commerce.service.core.PaymentChannelException;
import realworld_backend.commerce.service.core.WebhookDecision;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultWebhookErrorPolicyTest {

    private final DefaultWebhookErrorPolicy policy = new DefaultWebhookErrorPolicy();

    @Test
    void orderFailedShouldBeTerminalAndReturn200() {
        WebhookDecision decision = policy.classify(new BizException(ErrorCode.ORDER_FAILED));

        assertTrue(decision.terminal());
        assertEquals(HttpStatus.OK, decision.httpStatus());
        assertEquals(EventStatus.DEAD, decision.eventStatus());
        assertFalse(decision.needUpsertAbnormal());
    }

    @Test
    void retryableChannelExceptionShouldReturn500() {
        PaymentChannelException exception = new PaymentChannelException(
                "STRIPE",
                "processing_error",
                "req_1",
                true,
                "retry later",
                null
        );

        WebhookDecision decision = policy.classify(exception);
        assertFalse(decision.terminal());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, decision.httpStatus());
        assertEquals(EventStatus.FAILED, decision.eventStatus());
    }

    @Test
    void nonRetryableChannelExceptionShouldReturn200AndManualReview() {
        PaymentChannelException exception = new PaymentChannelException(
                "STRIPE",
                "incorrect_number",
                "req_2",
                false,
                "hard decline",
                null
        );

        WebhookDecision decision = policy.classify(exception);
        assertTrue(decision.terminal());
        assertEquals(HttpStatus.OK, decision.httpStatus());
        assertEquals(EventStatus.DEAD, decision.eventStatus());
        assertTrue(decision.needUpsertAbnormal());
    }
}

