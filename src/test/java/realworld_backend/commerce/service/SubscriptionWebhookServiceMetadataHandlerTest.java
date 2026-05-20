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
import realworld_backend.commerce.model.invoice.InvoiceWebhookEvent;
import realworld_backend.commerce.model.subscription.SubscriptionWebhookEvent;
import realworld_backend.commerce.service.core.WebhookContext;
import realworld_backend.commerce.service.impl.parser.WebhookObjectParserRouter;
import realworld_backend.commerce.service.subscription.CustomerSubscriptionService;
import realworld_backend.commerce.service.subscription.SubscriptionHistoryService;
import realworld_backend.commerce.service.subscription.SubscriptionWebhookService;
import realworld_backend.commerce.support.WebhookTestPayloadFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    private RLock lock;

    private SubscriptionWebhookService subscriptionWebhookService;

    @BeforeEach
    void setUp() {
        subscriptionWebhookService = new SubscriptionWebhookService(
                webhookObjectParserRouter,
                redissonClient,
                subscriptionHistoryService,
                abnormalOrchestrator,
                redisTemplate,
                customerSubscriptionService,
                invoiceService
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
}
