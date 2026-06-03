package realworld_backend.commerce.service;

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
import realworld_backend.commerce.model.PaymentStatus;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.model.invoice.InvoiceWebhookEvent;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.SubscriptionWebhookEvent;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.service.entitlement.EntitlementProjector;
import realworld_backend.commerce.service.webhook.PaymentFailureEscalationService;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.webhook.parser.WebhookObjectParserRouter;
import realworld_backend.commerce.service.statemachine.SubscriptionWebhookStateMachine;
import realworld_backend.commerce.service.subscription.CustomerSubscriptionService;
import realworld_backend.commerce.service.subscription.invoice.SubscriptionInvoiceWebhookService;
import realworld_backend.commerce.service.subscription.lifecycle.SubscriptionLifecycleWebhookService;
import realworld_backend.commerce.service.subscription.snapshot.SubscriptionSnapshotMergeService;
import realworld_backend.commerce.service.subscription.SubscriptionHistoryService;
import realworld_backend.commerce.service.subscription.lifecycle.SubscriptionSnapshotSyncService;
import realworld_backend.commerce.service.subscription.SubscriptionWebhookService;
import realworld_backend.commerce.support.WebhookTestPayloadFactory;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionWebhookServiceMetadataHandlerTest {

    @Mock
    private WebhookObjectParserRouter webhookObjectParserRouter;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private SubscriptionHistoryService subscriptionHistoryService;
    @Mock
    private AbnormalOrchestrator abnormalOrchestrator;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private CustomerSubscriptionService customerSubscriptionService;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private PaymentFailureEscalationService paymentFailureEscalationService;
    @Mock
    private EntitlementProjector entitlementProjector;
    private SubscriptionSnapshotMergeService subscriptionSnapshotMergeService;
    @Mock
    private RLock lock;

    private SubscriptionWebhookService subscriptionWebhookService;
    private SubscriptionSnapshotSyncService subscriptionSnapshotSyncService;
    private SubscriptionWebhookStateMachine subscriptionWebhookStateMachine;
    private SubscriptionLifecycleWebhookService subscriptionLifecycleWebhookService;
    private SubscriptionInvoiceWebhookService subscriptionInvoiceWebhookService;

    @BeforeEach
    void setUp() {
        subscriptionWebhookStateMachine = new SubscriptionWebhookStateMachine();
        subscriptionSnapshotMergeService = new SubscriptionSnapshotMergeService();
        subscriptionLifecycleWebhookService = new SubscriptionLifecycleWebhookService(
                webhookObjectParserRouter,
                redissonClient,
                subscriptionHistoryService,
                abnormalOrchestrator,
                redisTemplate,
                customerSubscriptionService,
                paymentFailureEscalationService,
                subscriptionWebhookStateMachine,
                subscriptionSnapshotMergeService,
                entitlementProjector
        );
        subscriptionInvoiceWebhookService = new SubscriptionInvoiceWebhookService(
                webhookObjectParserRouter,
                redissonClient,
                abnormalOrchestrator,
                redisTemplate,
                customerSubscriptionService,
                invoiceService,
                paymentFailureEscalationService,
                subscriptionWebhookStateMachine,
                subscriptionSnapshotMergeService,
                entitlementProjector
        );
        subscriptionSnapshotSyncService = new SubscriptionSnapshotSyncService(
                webhookObjectParserRouter,
                redissonClient,
                redisTemplate,
                customerSubscriptionService,
                abnormalOrchestrator,
                subscriptionSnapshotMergeService
        );
        subscriptionWebhookService = new SubscriptionWebhookService(
                subscriptionLifecycleWebhookService,
                subscriptionInvoiceWebhookService,
                subscriptionSnapshotSyncService
        );

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(false);
    }

    @Test
    void subscriptionCreatedShouldUseMetadataWithoutFallbackLookup() {
        ProviderRawEvent rawEvent = WebhookTestPayloadFactory.subscriptionCreatedRawEvent(
                "evt_sub_created_meta",
                "sub_123",
                "sub_no_123"
        );
        SubscriptionWebhookEvent event = SubscriptionWebhookEvent.parseProviderRawEvent(rawEvent);
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_sub_created_meta")
                .eventType(BusinessEventType.SUBSCRIPTION_CREATED)
                .providerRawEvent(rawEvent)
                .build();

        doReturn(event)
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(SubscriptionWebhookEvent.class));
        when(valueOperations.get(anyString())).thenReturn("idempotent_hit");

        assertDoesNotThrow(() -> subscriptionWebhookService.handleSubscriptionCreatedEvent(ctx));

        assertEquals("sub_no_123", ctx.getTrackingId());
        assertEquals("sub_123", ctx.getProviderTrackingId());
        verify(customerSubscriptionService, never()).findByProviderSubscriptionId(anyString());
    }

    @Test
    void subscriptionDeletedShouldUseMetadataWithoutFallbackLookup() {
        ProviderRawEvent rawEvent = WebhookTestPayloadFactory.subscriptionDeletedRawEvent(
                "evt_sub_deleted_meta",
                "sub_456",
                "sub_no_456"
        );
        SubscriptionWebhookEvent event = SubscriptionWebhookEvent.parseProviderRawEvent(rawEvent);
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_sub_deleted_meta")
                .eventType(BusinessEventType.SUBSCRIPTION_DELETED)
                .providerRawEvent(rawEvent)
                .build();

        doReturn(event)
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(SubscriptionWebhookEvent.class));
        when(valueOperations.get(anyString())).thenReturn("idempotent_hit");

        assertDoesNotThrow(() -> subscriptionWebhookService.handleSubscriptionDeletedEvent(ctx));

        assertEquals("sub_no_456", ctx.getTrackingId());
        assertEquals("sub_456", ctx.getProviderTrackingId());
        verify(customerSubscriptionService, never()).findByProviderSubscriptionId(anyString());
    }

    @Test
    void subscriptionCreatedIgnoreShouldStillSyncSnapshotFields() throws Exception {
        ProviderRawEvent rawEvent = WebhookTestPayloadFactory.subscriptionCreatedRawEvent(
                "evt_sub_created_ignore",
                "sub_123",
                "sub_no_123"
        );
        SubscriptionWebhookEvent event = SubscriptionWebhookEvent.parseProviderRawEvent(rawEvent);
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_sub_created_ignore")
                .eventType(BusinessEventType.SUBSCRIPTION_CREATED)
                .providerRawEvent(rawEvent)
                .build();

        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_123")
                .status(SubscriptionStatus.ACTIVE)
                .build();

        doReturn(event)
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(SubscriptionWebhookEvent.class));
        when(valueOperations.get(anyString())).thenReturn(null);
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any());
        when(customerSubscriptionService.findBySubscriptionNo("sub_no_123")).thenReturn(Optional.of(local));

        assertDoesNotThrow(() -> subscriptionWebhookService.handleSubscriptionCreatedEvent(ctx));

        assertEquals("sub_123", local.getProviderSubscriptionId());
        assertEquals("cus_123", local.getProviderCustomerId());
        assertNotNull(local.getCurrentPeriodStart());
        assertNotNull(local.getCurrentPeriodEnd());
        assertEquals(Boolean.FALSE, local.getCancelAtPeriodEnd());
        verify(customerSubscriptionService).save(local);
    }

    @Test
    void subscriptionCreatedShouldRefreshEntitlementAfterActivation() throws Exception {
        ProviderRawEvent rawEvent = WebhookTestPayloadFactory.subscriptionCreatedRawEvent(
                "evt_sub_created_apply",
                "sub_321",
                "sub_no_321"
        );
        SubscriptionWebhookEvent event = SubscriptionWebhookEvent.parseProviderRawEvent(rawEvent);
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_sub_created_apply")
                .eventType(BusinessEventType.SUBSCRIPTION_CREATED)
                .providerRawEvent(rawEvent)
                .build();

        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_321")
                .status(SubscriptionStatus.PAYING)
                .build();

        doReturn(event)
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(SubscriptionWebhookEvent.class));
        when(valueOperations.get(anyString())).thenReturn(null);
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any());
        when(customerSubscriptionService.findBySubscriptionNo("sub_no_321")).thenReturn(Optional.of(local));
        when(customerSubscriptionService.updateFromProviderIfStatusChanged(
                eq("sub_no_321"),
                any(),
                eq(SubscriptionStatus.ACTIVE),
                eq(event.getObject().currentPeriodStartInstant()),
                eq(event.getObject().currentPeriodEndInstant()),
                eq(Boolean.FALSE),
                eq(false),
                any()
        )).thenReturn(1);
        when(subscriptionHistoryService.appendActiveEventIfLatestPaid("sub_no_321"))
                .thenReturn(new SubscriptionHistoryService.AppendResult(
                        SubscriptionHistoryService.AppendOutcome.APPENDED
                ));

        assertDoesNotThrow(() -> subscriptionWebhookService.handleSubscriptionCreatedEvent(ctx));

        verify(entitlementProjector).refreshSubscriptionEntitlement(eq(local), any());
    }

    @Test
    void invoiceParseShouldReadSubscriptionDetailsMetadata() {
        ProviderRawEvent rawEvent = WebhookTestPayloadFactory.invoicePaymentSucceededRawEvent(
                "evt_invoice_succeeded_meta",
                "in_789",
                "sub_no_789"
        );

        InvoiceWebhookEvent event = InvoiceWebhookEvent.parseProviderRawEvent(rawEvent);

        assertEquals("sub_no_789", event.getObject().resolveMetadataValue("subscriptionNo"));
        assertEquals("in_789", event.getObject().getId());
        assertEquals("sub_123", event.getObject().getSubscription());
    }

    @Test
    void subscriptionUpdatedShouldUseLifecycleProviderStatusAndSyncSnapshotUsingMetadata() throws Exception {
        ProviderRawEvent rawEvent = WebhookTestPayloadFactory.subscriptionUpdatedRawEvent(
                "evt_sub_updated_meta",
                "sub_789",
                "sub_no_789",
                "active"
        );
        SubscriptionWebhookEvent event = SubscriptionWebhookEvent.parseProviderRawEvent(rawEvent);
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_sub_updated_meta")
                .eventType(BusinessEventType.SUBSCRIPTION_UPDATED)
                .providerRawEvent(rawEvent)
                .build();

        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_789")
                .status(SubscriptionStatus.PENDING)
                .build();

        doReturn(event)
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(SubscriptionWebhookEvent.class));
        when(valueOperations.get(anyString())).thenReturn(null);
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any());
        when(customerSubscriptionService.findBySubscriptionNo("sub_no_789")).thenReturn(Optional.of(local));
        when(customerSubscriptionService.updateFromProviderIfStatusChanged(
                eq("sub_no_789"),
                any(),
                eq(SubscriptionStatus.ACTIVE),
                eq(event.getObject().currentPeriodStartInstant()),
                eq(event.getObject().currentPeriodEndInstant()),
                eq(Boolean.FALSE),
                eq(false),
                any()
        )).thenReturn(1);

        assertDoesNotThrow(() -> subscriptionWebhookService.handleSubscriptionLifecycleUpdatedEvent(ctx));

        assertEquals("sub_no_789", ctx.getTrackingId());
        assertEquals("sub_789", ctx.getProviderTrackingId());
        assertEquals("sub_789", local.getProviderSubscriptionId());
        assertEquals("cus_123", local.getProviderCustomerId());
        assertNotNull(local.getCurrentPeriodStart());
        assertNotNull(local.getCurrentPeriodEnd());
        verify(customerSubscriptionService, times(3)).findBySubscriptionNo("sub_no_789");
        verify(customerSubscriptionService).updateFromProviderIfStatusChanged(
                eq("sub_no_789"),
                any(),
                eq(SubscriptionStatus.ACTIVE),
                eq(event.getObject().currentPeriodStartInstant()),
                eq(event.getObject().currentPeriodEndInstant()),
                eq(Boolean.FALSE),
                eq(false),
                any()
        );
        verify(customerSubscriptionService).save(local);
        verify(entitlementProjector).refreshSubscriptionEntitlement(eq(local), any());
    }

    @Test
    void subscriptionUpdatedShouldReadBillingPeriodFromItemsWhenTopLevelPeriodMissing() {
        ProviderRawEvent rawEvent = WebhookTestPayloadFactory.subscriptionUpdatedRawEventWithItemPeriods(
                "evt_sub_updated_item_periods",
                "sub_790",
                "sub_no_790",
                "active",
                1779248316L,
                1781840316L
        );

        SubscriptionWebhookEvent event = SubscriptionWebhookEvent.parseProviderRawEvent(rawEvent);

        assertNotNull(event.getObject().currentPeriodStartInstant());
        assertNotNull(event.getObject().currentPeriodEndInstant());
        assertEquals(1779248316L, event.getObject().currentPeriodStartInstant().getEpochSecond());
        assertEquals(1781840316L, event.getObject().currentPeriodEndInstant().getEpochSecond());
    }

    @Test
    void olderLifecycleUpdatedEventShouldBeIgnoredBeforeTransition() throws Exception {
        ProviderRawEvent rawEvent = WebhookTestPayloadFactory.subscriptionUpdatedRawEvent(
                "evt_sub_updated_stale",
                "sub_790",
                "sub_no_790",
                "active"
        );
        rawEvent.setCreated(java.time.Instant.parse("2026-05-01T00:00:00Z").getEpochSecond());
        SubscriptionWebhookEvent event = SubscriptionWebhookEvent.parseProviderRawEvent(rawEvent);
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_sub_updated_stale")
                .eventType(BusinessEventType.SUBSCRIPTION_UPDATED)
                .providerRawEvent(rawEvent)
                .build();

        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_790")
                .status(SubscriptionStatus.PAST_DUE)
                .lastLifecycleEventCreatedAt(LocalDateTime.parse("2026-05-02T00:00:00"))
                .build();

        doReturn(event)
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(SubscriptionWebhookEvent.class));
        when(valueOperations.get(anyString())).thenReturn(null);
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any());
        when(customerSubscriptionService.findBySubscriptionNo("sub_no_790")).thenReturn(Optional.of(local));

        assertDoesNotThrow(() -> subscriptionWebhookService.handleSubscriptionLifecycleUpdatedEvent(ctx));

        verify(customerSubscriptionService, never()).updateFromProviderIfStatusChanged(
                anyString(), any(), any(), any(), any(), any(), anyBoolean(), any()
        );
        verify(entitlementProjector, never()).refreshSubscriptionEntitlement(any(), any());
    }

    @Test
    void invoiceActionRequiredShouldPersistProcessingInvoice() throws Exception {
        ProviderRawEvent rawEvent = WebhookTestPayloadFactory.invoicePaymentActionRequiredRawEvent(
                "evt_invoice_action_required",
                "in_999",
                "sub_no_999"
        );
        InvoiceWebhookEvent event = InvoiceWebhookEvent.parseProviderRawEvent(rawEvent);
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_invoice_action_required")
                .eventType(BusinessEventType.INVOICE_PAYMENT_ACTION_REQUIRED)
                .providerRawEvent(rawEvent)
                .build();

        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_999")
                .status(SubscriptionStatus.ACTIVE)
                .build();

        doReturn(event)
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(InvoiceWebhookEvent.class));
        when(valueOperations.get(anyString())).thenReturn(null);
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any());
        when(invoiceService.upsertInvoiceByEvent(event, "sub_no_999", PaymentStatus.PROCESSING)).thenReturn(true);
        when(customerSubscriptionService.findBySubscriptionNo("sub_no_999")).thenReturn(Optional.of(local));

        assertDoesNotThrow(() -> subscriptionWebhookService.handleInvoicePaymentActionRequired(ctx));

        assertEquals("sub_no_999", ctx.getTrackingId());
        assertEquals("in_999", ctx.getProviderTrackingId());
        verify(invoiceService).upsertInvoiceByEvent(event, "sub_no_999", PaymentStatus.PROCESSING);
        verify(customerSubscriptionService).save(local);
    }
}
