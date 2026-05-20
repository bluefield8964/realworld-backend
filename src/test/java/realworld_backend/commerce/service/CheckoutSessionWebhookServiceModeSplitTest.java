package realworld_backend.commerce.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.google.gson.JsonPrimitive;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.model.checkoutPayment.CheckoutSessionWebhookEvent;
import realworld_backend.commerce.service.core.WebhookContext;
import realworld_backend.commerce.service.impl.parser.WebhookObjectParserRouter;
import realworld_backend.commerce.service.subscription.CustomerSubscriptionService;
import realworld_backend.commerce.service.subscription.SubscriptionHistoryService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutSessionWebhookServiceModeSplitTest {

    @Mock
    private AbnormalOrchestrator abnormalOrchestrator;
    @Mock
    private realworld_backend.commerce.repository.OrderRepository orderRepository;
    @Mock
    private realworld_backend.commerce.repository.PaymentRepository paymentRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;
    @Mock
    private WebhookObjectParserRouter webhookObjectParserRouter;
    @Mock
    private PaymentService paymentService;
    @Mock
    private CustomerSubscriptionService customerSubscriptionService;
    @Mock
    private SubscriptionHistoryService subscriptionHistoryService;

    private CheckoutSessionWebhookService checkoutSessionWebhookService;

    @BeforeEach
    void setUp() {
        checkoutSessionWebhookService = new CheckoutSessionWebhookService(
                abnormalOrchestrator,
                orderRepository,
                paymentRepository,
                redisTemplate,
                redissonClient,
                webhookObjectParserRouter,
                paymentService,
                customerSubscriptionService,
                subscriptionHistoryService
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("idempotent_hit");
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(false);
    }

    @Test
    void checkoutCompletedModePaymentShouldRouteToOrderBranch() throws Exception {
        ProviderRawEvent rowEvent = buildCheckoutCompletedRowEvent();
        CheckoutSessionWebhookEvent event = buildCheckoutSessionEvent("payment");
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_checkout_payment")
                .eventType(BusinessEventType.CHECKOUT_SESSION_COMPLETED)
                .providerRawEvent(rowEvent)
                .build();

        doReturn(event)
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(CheckoutSessionWebhookEvent.class));

        try (MockedStatic<CheckoutSessionWebhookEvent> mockedStatic = mockStatic(CheckoutSessionWebhookEvent.class)) {
            mockedStatic.when(() -> CheckoutSessionWebhookEvent.parseProviderRawEvent(rowEvent)).thenReturn(event);
            assertDoesNotThrow(() -> checkoutSessionWebhookService.handleCheckoutSessionCompleted(ctx));
        }
        assertEquals("order_123", ctx.getTrackingId());
        assertEquals("cs_pay_123", ctx.getProviderTrackingId());
        verify(webhookObjectParserRouter).parseAs(
                any(),
                eq(BusinessEventType.CHECKOUT_SESSION_COMPLETED),
                eq(CheckoutSessionWebhookEvent.class)
        );
    }

    @Test
    void checkoutCompletedModeSubscriptionShouldRouteToSubscriptionBranch() throws Exception {
        ProviderRawEvent rowEvent = buildCheckoutCompletedRowEvent();
        CheckoutSessionWebhookEvent event = buildCheckoutSessionEvent("subscription");
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_checkout_subscription")
                .eventType(BusinessEventType.CHECKOUT_SESSION_COMPLETED)
                .providerRawEvent(rowEvent)
                .build();

        try (MockedStatic<CheckoutSessionWebhookEvent> mockedStatic = mockStatic(CheckoutSessionWebhookEvent.class)) {
            mockedStatic.when(() -> CheckoutSessionWebhookEvent.parseProviderRawEvent(rowEvent)).thenReturn(event);
            assertDoesNotThrow(() -> checkoutSessionWebhookService.handleCheckoutSessionCompleted(ctx));
        }
        assertEquals("sub_no_123", ctx.getTrackingId());
        assertEquals("cs_sub_123", ctx.getProviderTrackingId());
        verify(webhookObjectParserRouter, never()).parseAs(any(), any(), any());
    }

    private ProviderRawEvent buildCheckoutCompletedRowEvent() {
        return ProviderRawEvent.builder()
                .provider("STRIPE")
                .eventId("evt_checkout_completed")
                .type(BusinessEventType.CHECKOUT_SESSION_COMPLETED)
                .rawType("checkout.session.completed")
                .rawObjectJson("{}")
                .created(1715000000L)
                .livemode(false)
                .build();
    }

    private CheckoutSessionWebhookEvent buildCheckoutSessionEvent(String mode) {
        CheckoutSessionWebhookEvent.CheckoutSessionObject object =
                new CheckoutSessionWebhookEvent.CheckoutSessionObject();
        object.setStatus("complete");
        object.setPaymentStatus("paid");
        object.setMode(mode);
        if ("subscription".equals(mode)) {
            object.setId("cs_sub_123");
            object.setMetadata(java.util.Map.of("subscriptionNo", "sub_no_123"));
        } else {
            object.setId("cs_pay_123");
            object.setPaymentIntent(new JsonPrimitive("pi_123"));
            object.setAmountTotal(1000L);
            object.setMetadata(java.util.Map.of("orderNo", "order_123"));
        }

        return CheckoutSessionWebhookEvent.builder()
                .eventId("evt_checkout_completed")
                .eventType(BusinessEventType.CHECKOUT_SESSION_COMPLETED)
                .provider("STRIPE")
                .created(1715000000L)
                .object(object)
                .build();
    }
}

