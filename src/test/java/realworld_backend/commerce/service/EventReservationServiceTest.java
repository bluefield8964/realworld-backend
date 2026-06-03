package realworld_backend.commerce.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.BusinessEvent;
import realworld_backend.commerce.model.EventStatus;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.service.core.RetryPolicy;
import realworld_backend.commerce.service.webhook.BusinessEventService;
import realworld_backend.commerce.service.webhook.EventReservationService;
import realworld_backend.commerce.service.webhook.PaymentFailureEscalationService;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.webhook.core.WebhookDecision;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventReservationServiceTest {

    @Mock
    private BusinessEventService businessEventService;
    @Mock
    private RetryPolicy retryPolicy;
    @Mock
    private AbnormalOrchestrator abnormalOrchestrator;
    @Mock
    private PaymentFailureEscalationService paymentFailureEscalationService;

    private EventReservationService eventReservationService;

    @BeforeEach
    void setUp() {
        eventReservationService = new EventReservationService(
                businessEventService,
                retryPolicy,
                abnormalOrchestrator,
                paymentFailureEscalationService
        );
    }

    @Test
    void reserveOrTakeoverShouldReserveFirstSeenEvent() {
        WebhookContext ctx = baseContext("evt_first_seen", BusinessEventType.CHECKOUT_SESSION_COMPLETED);

        boolean alreadyHandled = eventReservationService.reserveOrTakeover(ctx);

        assertFalse(alreadyHandled);
        assertEquals(0, ctx.getAttempts());
        verify(businessEventService).saveProcessing("evt_first_seen", BusinessEventType.CHECKOUT_SESSION_COMPLETED);
        verifyNoInteractions(retryPolicy, abnormalOrchestrator, paymentFailureEscalationService);
    }

    @Test
    void reserveOrTakeoverShouldShortCircuitSucceededReplay() {
        String eventId = "evt_replay_1";
        BusinessEventType type = BusinessEventType.CHECKOUT_SESSION_COMPLETED;

        doThrow(new DataIntegrityViolationException("duplicate event"))
                .when(businessEventService)
                .saveProcessing(eventId, type);
        when(businessEventService.findByIdForUpdateOrThrow(eventId))
                .thenReturn(existingEvent(eventId, type, EventStatus.SUCCEEDED, 2, LocalDateTime.now()));

        WebhookContext ctx = baseContext(eventId, type);
        ctx.setProviderRawEvent(null);
        ctx.setProviderRawEvent(null);

        boolean alreadyHandled = eventReservationService.reserveOrTakeover(ctx);

        assertTrue(alreadyHandled);
        verify(businessEventService, never()).markEventStatusWithAttempt(
                anyString(), any(), any(), any(), anyInt(), anyInt()
        );
        verifyNoInteractions(abnormalOrchestrator, paymentFailureEscalationService);
    }

    @Test
    void reserveOrTakeoverShouldRecordDeadEventAndShortCircuit() {
        String eventId = "evt_dead_1";
        BusinessEventType type = BusinessEventType.CHECKOUT_SESSION_COMPLETED;

        doThrow(new DataIntegrityViolationException("duplicate event"))
                .when(businessEventService)
                .saveProcessing(eventId, type);
        when(businessEventService.findByIdForUpdateOrThrow(eventId))
                .thenReturn(existingEvent(eventId, type, EventStatus.DEAD, 4, LocalDateTime.now()));

        WebhookContext ctx = baseContext(eventId, type);
        ctx.setTrackingId("order_123");
        ctx.setProviderTrackingId("cs_123");

        boolean alreadyHandled = eventReservationService.reserveOrTakeover(ctx);

        assertTrue(alreadyHandled);
        assertTrue(ctx.isAbnormalAlreadyUpserted());
        assertEquals(4, ctx.getAttempts());
        verify(abnormalOrchestrator).upsertAbnormalOrder(
                eq("order_123"),
                eq(eventId),
                eq(type),
                eq("cs_123"),
                eq("event_already_dead"),
                eq("event already dead"),
                eq(realworld_backend.commerce.model.log.AbnormalOrderType.EVENT_RETRY_BUDGET_EXHAUSTED),
                eq("STRIPE"),
                eq(null)
        );
        verify(paymentFailureEscalationService).recordDeadEventIncident(
                eq(ctx),
                eq("event_already_dead"),
                eq("event already dead")
        );
    }

    @Test
    void reserveOrTakeoverDeadEventWithoutFallbackKeysShouldOnlyRecordIncident() {
        String eventId = "evt_dead_2";
        BusinessEventType type = BusinessEventType.CHECKOUT_SESSION_COMPLETED;

        doThrow(new DataIntegrityViolationException("duplicate event"))
                .when(businessEventService)
                .saveProcessing(eventId, type);
        when(businessEventService.findByIdForUpdateOrThrow(eventId))
                .thenReturn(existingEvent(eventId, type, EventStatus.DEAD, 3, LocalDateTime.now()));

        WebhookContext ctx = baseContext(eventId, type);
        ctx.setProviderRawEvent(null);

        boolean alreadyHandled = eventReservationService.reserveOrTakeover(ctx);

        assertTrue(alreadyHandled);
        verifyNoInteractions(abnormalOrchestrator);
        verify(paymentFailureEscalationService).recordDeadEventIncident(
                eq(ctx),
                eq("event_already_dead"),
                eq("event already dead")
        );
    }

    @Test
    void reserveOrTakeoverShouldMoveRetryExhaustedEventToDeadAndEscalate() {
        String eventId = "evt_retry_exhausted";
        BusinessEventType type = BusinessEventType.INVOICE_PAYMENT_FAILED;

        doThrow(new DataIntegrityViolationException("duplicate event"))
                .when(businessEventService)
                .saveProcessing(eventId, type);
        when(businessEventService.findByIdForUpdateOrThrow(eventId))
                .thenReturn(existingEvent(eventId, type, EventStatus.FAILED, 5, LocalDateTime.now().minusMinutes(10)));
        when(retryPolicy.exhausted(5)).thenReturn(true);

        WebhookContext ctx = baseContext(eventId, type);
        ctx.setTrackingId("sub_no_123");
        ctx.setProviderTrackingId("in_123");

        boolean alreadyHandled = eventReservationService.reserveOrTakeover(ctx);

        assertTrue(alreadyHandled);
        assertTrue(ctx.isAbnormalAlreadyUpserted());
        verify(businessEventService).markEventStatusWithAttempt(
                eq(eventId),
                eq(type),
                eq(EventStatus.DEAD),
                eq("max attempts exceeded"),
                eq(5),
                eq(0)
        );
        verify(abnormalOrchestrator).upsertAbnormalOrder(
                eq("sub_no_123"),
                eq(eventId),
                eq(type),
                eq("in_123"),
                eq("handleStripeEvent_max_attempts"),
                eq("max attempts exceeded"),
                eq(realworld_backend.commerce.model.log.AbnormalOrderType.EVENT_RETRY_BUDGET_EXHAUSTED),
                eq("STRIPE"),
                eq(null)
        );
        verify(paymentFailureEscalationService).recordRetryExhaustedIncident(
                eq(ctx),
                eq("handleStripeEvent_max_attempts"),
                eq("max attempts exceeded")
        );
    }

    @Test
    void reserveOrTakeoverShouldThrowWhenProcessingLeaseStillValid() {
        String eventId = "evt_processing_1";
        BusinessEventType type = BusinessEventType.SUBSCRIPTION_CREATED;
        LocalDateTime handledAt = LocalDateTime.now();

        doThrow(new DataIntegrityViolationException("duplicate event"))
                .when(businessEventService)
                .saveProcessing(eventId, type);
        when(businessEventService.findByIdForUpdateOrThrow(eventId))
                .thenReturn(existingEvent(eventId, type, EventStatus.PROCESSING, 1, handledAt));
        when(retryPolicy.exhausted(1)).thenReturn(false);
        when(retryPolicy.mainStreamNextRetryAt(eq(1), any(Instant.class)))
                .thenReturn(Instant.now().plusSeconds(120));

        WebhookContext ctx = baseContext(eventId, type);

        BizException exception = assertThrows(BizException.class, () -> eventReservationService.reserveOrTakeover(ctx));

        assertEquals(ErrorCode.EVENT_PROCESSING, exception.getErrorCode());
        verify(businessEventService, never()).markEventStatusWithAttempt(
                anyString(), any(), any(), any(), anyInt(), anyInt()
        );
    }

    @Test
    void reserveOrTakeoverShouldTakeOverStaleProcessingEvent() {
        String eventId = "evt_processing_stale";
        BusinessEventType type = BusinessEventType.SUBSCRIPTION_CREATED;
        LocalDateTime handledAt = LocalDateTime.now().minusHours(1);

        doThrow(new DataIntegrityViolationException("duplicate event"))
                .when(businessEventService)
                .saveProcessing(eventId, type);
        when(businessEventService.findByIdForUpdateOrThrow(eventId))
                .thenReturn(existingEvent(eventId, type, EventStatus.PROCESSING, 2, handledAt));
        when(retryPolicy.exhausted(2)).thenReturn(false);
        when(retryPolicy.mainStreamNextRetryAt(eq(2), any(Instant.class)))
                .thenReturn(Instant.now().minusSeconds(60));

        WebhookContext ctx = baseContext(eventId, type);

        boolean alreadyHandled = eventReservationService.reserveOrTakeover(ctx);

        assertFalse(alreadyHandled);
        assertEquals(2, ctx.getAttempts());
        verify(businessEventService).markEventStatusWithAttempt(
                eq(eventId),
                eq(type),
                eq(EventStatus.PROCESSING),
                eq(null),
                eq(2),
                eq(0)
        );
    }

    @Test
    void reserveOrTakeoverShouldTakeOverFailedEventWithinRetryBudget() {
        String eventId = "evt_failed_takeover";
        BusinessEventType type = BusinessEventType.INVOICE_PAYMENT_SUCCEEDED;

        doThrow(new DataIntegrityViolationException("duplicate event"))
                .when(businessEventService)
                .saveProcessing(eventId, type);
        when(businessEventService.findByIdForUpdateOrThrow(eventId))
                .thenReturn(existingEvent(eventId, type, EventStatus.FAILED, 3, LocalDateTime.now().minusMinutes(5)));
        when(retryPolicy.exhausted(3)).thenReturn(false);

        WebhookContext ctx = baseContext(eventId, type);

        boolean alreadyHandled = eventReservationService.reserveOrTakeover(ctx);

        assertFalse(alreadyHandled);
        assertEquals(3, ctx.getAttempts());
        verify(businessEventService).markEventStatusWithAttempt(
                eq(eventId),
                eq(type),
                eq(EventStatus.PROCESSING),
                eq(null),
                eq(3),
                eq(0)
        );
    }

    @Test
    void reserveOrTakeoverShouldThrowEventNotFoundWhenConflictRowIsInvisible() {
        String eventId = "evt_race_missing";
        BusinessEventType type = BusinessEventType.CHECKOUT_SESSION_COMPLETED;

        doThrow(new DataIntegrityViolationException("duplicate event"))
                .when(businessEventService)
                .saveProcessing(eventId, type);
        when(businessEventService.findByIdForUpdateOrThrow(eventId))
                .thenThrow(new RuntimeException("not visible yet"));

        WebhookContext ctx = baseContext(eventId, type);

        BizException exception = assertThrows(BizException.class, () -> eventReservationService.reserveOrTakeover(ctx));

        assertEquals(ErrorCode.EVENT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void markSuccessShouldDelegateToBusinessEventService() {
        WebhookContext ctx = baseContext("evt_success_mark", BusinessEventType.CHECKOUT_SESSION_COMPLETED);

        eventReservationService.markSuccess(ctx);

        verify(businessEventService).markSuccessAndIncrementAttempt(
                eq("evt_success_mark"),
                eq(EventStatus.SUCCEEDED),
                eq(null)
        );
    }

    @Test
    void markByDecisionShouldNotIncreaseAttemptWhenDecisionStaysProcessing() {
        WebhookContext ctx = baseContext("evt_processing_mark", BusinessEventType.CHECKOUT_SESSION_COMPLETED);
        ctx.setAttempts(2);

        eventReservationService.markByDecision(
                ctx,
                new WebhookDecision(false, EventStatus.PROCESSING, HttpStatus.OK, false, null),
                "keep processing"
        );

        verify(businessEventService).markEventStatusWithAttempt(
                eq("evt_processing_mark"),
                eq(BusinessEventType.CHECKOUT_SESSION_COMPLETED),
                eq(EventStatus.PROCESSING),
                eq("keep processing"),
                eq(2),
                eq(0)
        );
    }

    @Test
    void markByDecisionShouldIncreaseAttemptWhenDecisionIsTerminalOrFailed() {
        WebhookContext ctx = baseContext("evt_failed_mark", BusinessEventType.CHECKOUT_SESSION_COMPLETED);
        ctx.setAttempts(4);

        eventReservationService.markByDecision(
                ctx,
                new WebhookDecision(false, EventStatus.FAILED, HttpStatus.INTERNAL_SERVER_ERROR, false, null),
                "failed once"
        );

        verify(businessEventService).markEventStatusWithAttempt(
                eq("evt_failed_mark"),
                eq(BusinessEventType.CHECKOUT_SESSION_COMPLETED),
                eq(EventStatus.FAILED),
                eq("failed once"),
                eq(4),
                eq(1)
        );
    }

    private WebhookContext baseContext(String eventId, BusinessEventType type) {
        return WebhookContext.builder()
                .provider("STRIPE")
                .eventId(eventId)
                .eventType(type)
                .providerRawEvent(ProviderRawEvent.builder()
                        .provider("STRIPE")
                        .eventId(eventId)
                        .type(type)
                        .rawType(type.name())
                        .rawObjectJson("{\"id\":\"obj_123\"}")
                        .created(1715000000L)
                        .livemode(false)
                        .build())
                .build();
    }

    private BusinessEvent existingEvent(
            String eventId,
            BusinessEventType type,
            EventStatus status,
            int attempts,
            LocalDateTime handledAt
    ) {
        return BusinessEvent.builder()
                .eventId(eventId)
                .type(type)
                .status(status)
                .attempts(attempts)
                .eventHandledAt(handledAt)
                .build();
    }
}
