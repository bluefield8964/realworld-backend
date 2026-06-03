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
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.SubscriptionWebhookEvent;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionWebhookServiceSubscriptionFallbackTest {

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
    private SubscriptionWebhookStateMachine subscriptionWebhookStateMachine;
    private SubscriptionLifecycleWebhookService subscriptionLifecycleWebhookService;
    private SubscriptionInvoiceWebhookService subscriptionInvoiceWebhookService;
    private SubscriptionSnapshotSyncService subscriptionSnapshotSyncService;

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
        lenient().when(valueOperations.get(anyString())).thenReturn("idempotent_hit");
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(false);
    }

    @Test
    void subscriptionCreatedShouldFallbackByProviderSubscriptionIdWhenMetadataMissing() {
        ProviderRawEvent rowEvent = buildSubscriptionRowEvent(
                "evt_sub_created_1",
                BusinessEventType.SUBSCRIPTION_CREATED,
                "customer.subscription.created"
        );

        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_sub_created_1")
                .eventType(BusinessEventType.SUBSCRIPTION_CREATED)
                .providerRawEvent(rowEvent)
                .build();

        doReturn(buildSubscriptionEventWithoutMetadata("sub_123"))
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(SubscriptionWebhookEvent.class));
        when(customerSubscriptionService.findByProviderSubscriptionId("sub_123"))
                .thenReturn(Optional.of(CustomerSubscription.builder().subscriptionNo("sub_no_123").build()));

        assertDoesNotThrow(() -> subscriptionWebhookService.handleSubscriptionCreatedEvent(ctx));

        verify(customerSubscriptionService).findByProviderSubscriptionId("sub_123");
        assertEquals("sub_no_123", ctx.getTrackingId());
        assertEquals("sub_123", ctx.getProviderTrackingId());
    }

    @Test
    void subscriptionDeletedShouldFallbackByProviderSubscriptionIdWhenMetadataMissing() {
        ProviderRawEvent rowEvent = buildSubscriptionRowEvent(
                "evt_sub_deleted_1",
                BusinessEventType.SUBSCRIPTION_DELETED,
                "customer.subscription.deleted"
        );

        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_sub_deleted_1")
                .eventType(BusinessEventType.SUBSCRIPTION_DELETED)
                .providerRawEvent(rowEvent)
                .build();

        doReturn(buildSubscriptionEventWithoutMetadata("sub_456"))
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(SubscriptionWebhookEvent.class));
        when(customerSubscriptionService.findByProviderSubscriptionId("sub_456"))
                .thenReturn(Optional.of(CustomerSubscription.builder().subscriptionNo("sub_no_456").build()));

        assertDoesNotThrow(() -> subscriptionWebhookService.handleSubscriptionDeletedEvent(ctx));

        verify(customerSubscriptionService).findByProviderSubscriptionId("sub_456");
        assertEquals("sub_no_456", ctx.getTrackingId());
        assertEquals("sub_456", ctx.getProviderTrackingId());
    }

    private ProviderRawEvent buildSubscriptionRowEvent(
            String eventId,
            BusinessEventType type,
            String rawType
    ) {
        return ProviderRawEvent.builder()
                .provider("STRIPE")
                .eventId(eventId)
                .type(type)
                .rawType(rawType)
                .rawObjectJson("{}")
                .created(1715000000L)
                .livemode(false)
                .build();
    }

    private SubscriptionWebhookEvent buildSubscriptionEventWithoutMetadata(String providerSubscriptionId) {
        SubscriptionWebhookEvent.SubscriptionObject object = new SubscriptionWebhookEvent.SubscriptionObject();
        object.setId(providerSubscriptionId);
        object.setCustomer("cus_123");
        object.setStatus("active");

        return SubscriptionWebhookEvent.builder()
                .eventId("evt_test_subscription")
                .eventType(BusinessEventType.SUBSCRIPTION_CREATED)
                .provider("STRIPE")
                .created(1715000000L)
                .object(object)
                .build();
    }
}
