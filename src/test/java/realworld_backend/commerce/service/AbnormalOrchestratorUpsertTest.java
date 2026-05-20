package realworld_backend.commerce.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.log.AbnormalDomainType;
import realworld_backend.commerce.model.log.AbnormalOrder;
import realworld_backend.commerce.model.log.AbnormalOrderStatus;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.repository.AbnormalOrderRepository;
import realworld_backend.commerce.service.order.OrderAbnormalService;
import realworld_backend.commerce.service.subscription.SubscriptionAbnormalService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbnormalOrchestratorUpsertTest {

    @Mock
    private AbnormalOrderRepository abnormalOrderRepository;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private OrderAbnormalService orderAbnormalService;
    @Mock
    private SubscriptionAbnormalService subscriptionAbnormalService;

    private AbnormalOrchestrator abnormalOrchestrator;

    @BeforeEach
    void setUp() {
        abnormalOrchestrator = new AbnormalOrchestrator(
                abnormalOrderRepository,
                redissonClient,
                orderAbnormalService,
                subscriptionAbnormalService
        );
    }

    @Test
    void upsertAbnormalOrderShouldIgnoreTerminalExistingRecord() {
        AbnormalOrder existing = AbnormalOrder.builder()
                .sessionId("cs_123")
                .status(AbnormalOrderStatus.FIXED)
                .abnormalType(AbnormalOrderType.ORDER_MISSING)
                .retryCount(3)
                .errorMessage("handled")
                .build();

        when(abnormalOrderRepository.findBySessionIdForUpdate("cs_123"))
                .thenReturn(Optional.of(existing));

        abnormalOrchestrator.upsertAbnormalOrder(
                null,
                "evt_123",
                BusinessEventType.ORDER_PAYMENT_FAILED,
                "cs_123",
                "duplicate_event",
                "duplicate error",
                AbnormalOrderType.PAYMENT_MISSING,
                "STRIPE",
                null
        );

        verify(abnormalOrderRepository, never()).save(any());
    }

    @Test
    void upsertAbnormalOrderShouldPreserveStatusRetryCountAndOriginalAbnormalTypeForNonTerminalExistingRecord() {
        LocalDateTime originalNextRetryAt = LocalDateTime.now().plusMinutes(5);
        AbnormalOrder existing = AbnormalOrder.builder()
                .sessionId("cs_123")
                .orderNo("order_123")
                .domainType(AbnormalDomainType.ORDER)
                .status(AbnormalOrderStatus.RECONCILING)
                .abnormalType(AbnormalOrderType.ORDER_MISSING)
                .retryCount(2)
                .errorMessage("old error")
                .nextRetryAt(originalNextRetryAt)
                .build();

        when(abnormalOrderRepository.findBySessionIdForUpdate("cs_123"))
                .thenReturn(Optional.of(existing));
        when(abnormalOrderRepository.save(any(AbnormalOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        abnormalOrchestrator.upsertAbnormalOrder(
                "order_123",
                "evt_456",
                BusinessEventType.ORDER_PAYMENT_FAILED,
                "cs_123",
                "duplicate_event",
                "new error",
                AbnormalOrderType.PAYMENT_MISSING,
                "STRIPE",
                null
        );

        ArgumentCaptor<AbnormalOrder> captor = ArgumentCaptor.forClass(AbnormalOrder.class);
        verify(abnormalOrderRepository).save(captor.capture());
        AbnormalOrder saved = captor.getValue();
        assertEquals(AbnormalOrderStatus.RECONCILING, saved.getStatus());
        assertEquals(2, saved.getRetryCount());
        assertEquals(originalNextRetryAt, saved.getNextRetryAt());
        assertEquals(AbnormalOrderType.ORDER_MISSING, saved.getAbnormalType());
        assertTrue(saved.getErrorMessage().contains("old error"));
        assertTrue(saved.getErrorMessage().contains("new error"));
        assertEquals("evt_456", saved.getEventId());
    }
}
