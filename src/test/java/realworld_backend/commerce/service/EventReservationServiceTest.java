package realworld_backend.commerce.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.BusinessEvent;
import realworld_backend.commerce.model.EventStatus;
import realworld_backend.commerce.service.core.RetryPolicy;
import realworld_backend.commerce.service.core.WebhookContext;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private WebhookIncidentOrchestrator webhookIncidentOrchestrator;
    private EventReservationService eventReservationService;
    private PaymentFailureEscalationService paymentFailureEscalationService;

    @BeforeEach
    void setUp() {
        eventReservationService = new EventReservationService(
                businessEventService,
                retryPolicy,
                abnormalOrchestrator,paymentFailureEscalationService
        );
    }

    @Test
    void sameEventIdReplayShouldShortCircuitWhenAlreadySucceeded() {
        String eventId = "evt_replay_1";
        BusinessEventType type = BusinessEventType.CHECKOUT_SESSION_COMPLETED;

        doThrow(new DataIntegrityViolationException("duplicate event"))
                .when(businessEventService)
                .saveProcessing(eventId, type);
        when(businessEventService.findByIdForUpdateOrThrow(eventId))
                .thenReturn(
                        BusinessEvent.builder()
                                .eventId(eventId)
                                .type(type)
                                .status(EventStatus.SUCCEEDED)
                                .attempts(2)
                                .build()
                );

        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId(eventId)
                .eventType(type)
                .trackingId("cs_test_123")
                .build();

        boolean alreadyHandled = eventReservationService.reserveOrTakeover(ctx);

        assertTrue(alreadyHandled);
        verify(businessEventService, never()).markEventStatusWithAttempt(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()
        );
        verifyNoInteractions(abnormalOrchestrator);
    }
}

