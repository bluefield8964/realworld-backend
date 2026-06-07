package realworld_backend.commerce.service.subscription.checkout;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.checkoutPayment.CheckoutSessionWebhookEvent;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.service.AbnormalOrchestrator;
import realworld_backend.commerce.service.entitlement.EntitlementProjector;
import realworld_backend.commerce.service.metrics.CommerceMetricsService;
import realworld_backend.commerce.service.statemachine.SubscriptionWebhookStateMachine;
import realworld_backend.commerce.service.subscription.CustomerSubscriptionService;
import realworld_backend.commerce.service.subscription.SubscriptionHistoryService;
import realworld_backend.commerce.service.subscription.snapshot.SubscriptionSnapshotMergeService;
import realworld_backend.commerce.service.webhook.PaymentFailureEscalationService;
import realworld_backend.commerce.service.webhook.core.WebhookContext;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionCheckoutSessionWebhookServiceTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private AbnormalOrchestrator abnormalOrchestrator;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private CustomerSubscriptionService customerSubscriptionService;
    @Mock
    private SubscriptionHistoryService subscriptionHistoryService;
    @Mock
    private PaymentFailureEscalationService paymentFailureEscalationService;
    @Mock
    private EntitlementProjector entitlementProjector;
    private SubscriptionSnapshotMergeService subscriptionSnapshotMergeService;
    @Mock
    private RLock lock;
    @Mock
    private CommerceMetricsService commerceMetricsService;

    private SubscriptionCheckoutSessionWebhookService service;

    @BeforeEach
    void setUp() {
        subscriptionSnapshotMergeService = new SubscriptionSnapshotMergeService();
        service = new SubscriptionCheckoutSessionWebhookService(
                redissonClient,
                abnormalOrchestrator,
                redisTemplate,
                customerSubscriptionService,
                subscriptionHistoryService,
                paymentFailureEscalationService,
                new SubscriptionWebhookStateMachine(),
                subscriptionSnapshotMergeService,
                entitlementProjector, commerceMetricsService
        );

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(false);
    }

    @Test
    void checkoutCompletedShouldUseStateMachineTransitionAndAppendPayingHistory() throws Exception {
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_sub_checkout_completed")
                .eventType(BusinessEventType.CHECKOUT_SESSION_COMPLETED)
                .build();

        CheckoutSessionWebhookEvent.CheckoutSessionObject session = new CheckoutSessionWebhookEvent.CheckoutSessionObject();
        session.setId("cs_sub_123");
        session.setMode("subscription");
        session.setMetadata(Map.of("subscriptionNo", "sub_no_123"));

        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_123")
                .status(SubscriptionStatus.PENDING)
                .build();

        when(valueOperations.get(anyString())).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(customerSubscriptionService.findBySubscriptionNo("sub_no_123")).thenReturn(Optional.of(local));
        when(customerSubscriptionService.updateFromProviderIfStatusChanged(
                eq("sub_no_123"),
                any(),
                eq(SubscriptionStatus.PAYING),
                eq(null),
                eq(null),
                eq(null),
                eq(false),
                any()
        )).thenReturn(1);
        when(subscriptionHistoryService.appendPayingEventIfLatestPending("sub_no_123"))
                .thenReturn(new SubscriptionHistoryService.AppendResult(
                        SubscriptionHistoryService.AppendOutcome.APPENDED
                ));

        assertDoesNotThrow(() -> service.handleSubscriptionCheckoutCompletedEvent(ctx, session));

        assertEquals("sub_no_123", ctx.getTrackingId());
        assertEquals("cs_sub_123", ctx.getProviderTrackingId());
        verify(customerSubscriptionService).updateFromProviderIfStatusChanged(
                eq("sub_no_123"),
                any(),
                eq(SubscriptionStatus.PAYING),
                eq(null),
                eq(null),
                eq(null),
                eq(false),
                any()
        );
        verify(subscriptionHistoryService).appendPayingEventIfLatestPending("sub_no_123");
        verify(entitlementProjector).refreshSubscriptionEntitlement(eq(local), any());
    }

    @Test
    void checkoutFailedIgnoreShouldStillAttemptCheckoutFailHistoryAppend() throws Exception {
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_sub_checkout_failed")
                .eventType(BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED)
                .build();

        CheckoutSessionWebhookEvent.CheckoutSessionObject session = new CheckoutSessionWebhookEvent.CheckoutSessionObject();
        session.setId("cs_sub_456");
        session.setMode("subscription");
        session.setMetadata(Map.of("subscriptionNo", "sub_no_456"));

        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_456")
                .status(SubscriptionStatus.CHECKOUT_FAIL)
                .build();

        when(valueOperations.get(anyString())).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(customerSubscriptionService.findBySubscriptionNo("sub_no_456")).thenReturn(Optional.of(local));
        when(subscriptionHistoryService.appendCheckoutFailEventIfLatestPaying("sub_no_456"))
                .thenReturn(new SubscriptionHistoryService.AppendResult(
                        SubscriptionHistoryService.AppendOutcome.LATEST_PAYMENT_STATUS_MISMATCH
                ));

        assertDoesNotThrow(() -> service.handleSubscriptionCheckoutFailedEvent(ctx, session));

        verify(subscriptionHistoryService).appendCheckoutFailEventIfLatestPaying("sub_no_456");
    }

    @Test
    void checkoutCompletedShouldFallbackToOrderNoWhenSubscriptionNoMissing() throws Exception {
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_sub_checkout_completed_order_no")
                .eventType(BusinessEventType.CHECKOUT_SESSION_COMPLETED)
                .build();

        CheckoutSessionWebhookEvent.CheckoutSessionObject session = new CheckoutSessionWebhookEvent.CheckoutSessionObject();
        session.setId("cs_sub_789");
        session.setMode("subscription");
        session.setMetadata(Map.of("orderNo", "sub_no_789"));

        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_789")
                .status(SubscriptionStatus.PENDING)
                .build();

        when(valueOperations.get(anyString())).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(customerSubscriptionService.findBySubscriptionNo("sub_no_789")).thenReturn(Optional.of(local));
        when(customerSubscriptionService.updateFromProviderIfStatusChanged(
                eq("sub_no_789"),
                any(),
                eq(SubscriptionStatus.PAYING),
                eq(null),
                eq(null),
                eq(null),
                eq(false),
                any()
        )).thenReturn(1);
        when(subscriptionHistoryService.appendPayingEventIfLatestPending("sub_no_789"))
                .thenReturn(new SubscriptionHistoryService.AppendResult(
                        SubscriptionHistoryService.AppendOutcome.APPENDED
                ));

        assertDoesNotThrow(() -> service.handleSubscriptionCheckoutCompletedEvent(ctx, session));

        assertEquals("sub_no_789", ctx.getTrackingId());
        assertEquals("cs_sub_789", ctx.getProviderTrackingId());
        verify(customerSubscriptionService, atLeastOnce()).findBySubscriptionNo("sub_no_789");
        verify(subscriptionHistoryService).appendPayingEventIfLatestPending("sub_no_789");
    }

    @Test
    void checkoutExpiredShouldAppendDedicatedExpiredHistory() throws Exception {
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_sub_checkout_expired")
                .eventType(BusinessEventType.CHECKOUT_SESSION_EXPIRED)
                .build();

        CheckoutSessionWebhookEvent.CheckoutSessionObject session = new CheckoutSessionWebhookEvent.CheckoutSessionObject();
        session.setId("cs_sub_999");
        session.setMode("subscription");
        session.setMetadata(Map.of("subscriptionNo", "sub_no_999"));

        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_999")
                .status(SubscriptionStatus.PENDING)
                .build();

        when(valueOperations.get(anyString())).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(customerSubscriptionService.findBySubscriptionNo("sub_no_999")).thenReturn(Optional.of(local));
        when(customerSubscriptionService.updateFromProviderIfStatusChanged(
                eq("sub_no_999"),
                any(),
                eq(SubscriptionStatus.CHECKOUT_EXPIRED),
                eq(null),
                eq(null),
                eq(null),
                eq(true),
                any()
        )).thenReturn(1);
        when(subscriptionHistoryService.appendCheckoutExpiredEventIfLatestPaying("sub_no_999"))
                .thenReturn(new SubscriptionHistoryService.AppendResult(
                        SubscriptionHistoryService.AppendOutcome.APPENDED
                ));

        assertDoesNotThrow(() -> service.handleSubscriptionSessionExpired(ctx, session));

        verify(customerSubscriptionService).updateFromProviderIfStatusChanged(
                eq("sub_no_999"),
                any(),
                eq(SubscriptionStatus.CHECKOUT_EXPIRED),
                eq(null),
                eq(null),
                eq(null),
                eq(true),
                any()
        );
        verify(subscriptionHistoryService).appendCheckoutExpiredEventIfLatestPaying("sub_no_999");
        verify(entitlementProjector).refreshSubscriptionEntitlement(eq(local), any());
    }

    @Test
    void olderCheckoutEventShouldBeIgnoredBeforeTransition() throws Exception {
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_sub_checkout_stale")
                .eventType(BusinessEventType.CHECKOUT_SESSION_COMPLETED)
                .providerRawEvent(realworld_backend.commerce.model.core.ProviderRawEvent.builder()
                        .provider("STRIPE")
                        .eventId("evt_sub_checkout_stale")
                        .type(BusinessEventType.CHECKOUT_SESSION_COMPLETED)
                        .created(java.time.Instant.parse("2026-05-01T00:00:00Z").getEpochSecond())
                        .rawObjectJson("{}")
                        .build())
                .build();

        CheckoutSessionWebhookEvent.CheckoutSessionObject session = new CheckoutSessionWebhookEvent.CheckoutSessionObject();
        session.setId("cs_sub_stale");
        session.setMode("subscription");
        session.setMetadata(Map.of("subscriptionNo", "sub_no_stale"));

        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_stale")
                .status(SubscriptionStatus.CHECKOUT_FAIL)
                .lastCheckoutEventCreatedAt(LocalDateTime.parse("2026-05-02T00:00:00"))
                .build();

        when(valueOperations.get(anyString())).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(customerSubscriptionService.findBySubscriptionNo("sub_no_stale")).thenReturn(Optional.of(local));

        assertDoesNotThrow(() -> service.handleSubscriptionCheckoutCompletedEvent(ctx, session));

        verify(customerSubscriptionService, never()).updateFromProviderIfStatusChanged(
                anyString(), any(), any(), any(), any(), any(), anyBoolean(), any()
        );
        verify(entitlementProjector, never()).refreshSubscriptionEntitlement(any(), any());
    }
}
