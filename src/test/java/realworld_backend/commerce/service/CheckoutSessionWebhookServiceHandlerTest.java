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
import realworld_backend.commerce.model.Order;
import realworld_backend.commerce.model.OrderStatus;
import realworld_backend.commerce.model.Payment;
import realworld_backend.commerce.model.PaymentStatus;
import realworld_backend.commerce.model.checkoutPayment.CheckoutSessionWebhookEvent;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.service.core.WebhookContext;
import realworld_backend.commerce.service.impl.parser.WebhookObjectParserRouter;
import realworld_backend.commerce.service.subscription.CustomerSubscriptionService;
import realworld_backend.commerce.service.subscription.SubscriptionHistoryService;
import realworld_backend.commerce.support.WebhookTestPayloadFactory;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutSessionWebhookServiceHandlerTest {

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

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(false);
    }

    @Test
    void checkoutCompletedPaymentShouldAcceptMetadataBackedRawPayload() throws Exception {
        ProviderRawEvent rawEvent = WebhookTestPayloadFactory.checkoutCompletedPaymentRawEvent(
                "evt_checkout_completed_handler",
                "cs_pay_123",
                "order_123",
                "pi_123",
                3000L
        );
        CheckoutSessionWebhookEvent event = CheckoutSessionWebhookEvent.parseProviderRawEvent(rawEvent);
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_checkout_completed_handler")
                .eventType(BusinessEventType.CHECKOUT_SESSION_COMPLETED)
                .providerRawEvent(rawEvent)
                .build();

        doReturn(event)
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(CheckoutSessionWebhookEvent.class));
        when(valueOperations.get(anyString())).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(orderRepository.markPaidIfNotPaid(eq("cs_pay_123"), any())).thenReturn(1);
        when(paymentService.markPaidIfNotPaid("cs_pay_123")).thenReturn(1);

        assertDoesNotThrow(() -> checkoutSessionWebhookService.handleCheckoutSessionCompleted(ctx));

        assertEquals("order_123", ctx.getTrackingId());
        assertEquals("cs_pay_123", ctx.getProviderTrackingId());
        verify(orderRepository).markPaidIfNotPaid(eq("cs_pay_123"), any());
        verify(paymentService).markPaidIfNotPaid("cs_pay_123");
    }

    @Test
    void checkoutCompletedPaymentMissingMetadataShouldThrowJsonError() {
        ProviderRawEvent rawEvent = WebhookTestPayloadFactory.checkoutCompletedPaymentRawEventWithoutMetadata(
                "evt_checkout_completed_missing_metadata",
                "cs_pay_456",
                "pi_456",
                3000L
        );
        CheckoutSessionWebhookEvent event = CheckoutSessionWebhookEvent.parseProviderRawEvent(rawEvent);
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_checkout_completed_missing_metadata")
                .eventType(BusinessEventType.CHECKOUT_SESSION_COMPLETED)
                .providerRawEvent(rawEvent)
                .build();

        doReturn(event)
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(CheckoutSessionWebhookEvent.class));

        BizException exception = assertThrows(
                BizException.class,
                () -> checkoutSessionWebhookService.handleCheckoutSessionCompleted(ctx)
        );

        assertEquals(ErrorCode.JSON_ERROR, exception.getErrorCode());
    }

    @Test
    void checkoutAsyncPaymentFailedShouldPersistRetryableFailureWhenMetadataPresent() throws Exception {
        ProviderRawEvent rawEvent = WebhookTestPayloadFactory.checkoutAsyncPaymentFailedRawEvent(
                "evt_checkout_failed_handler",
                "cs_fail_123",
                "order_456",
                3000L
        );
        CheckoutSessionWebhookEvent event = CheckoutSessionWebhookEvent.parseProviderRawEvent(rawEvent);
        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_checkout_failed_handler")
                .eventType(BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED)
                .providerRawEvent(rawEvent)
                .build();

        Order order = Order.builder()
                .orderNo("order_456")
                .sessionId("cs_fail_123")
                .status(OrderStatus.PENDING)
                .activeKey("active_key")
                .build();
        Payment payment = Payment.builder()
                .orderNo("order_456")
                .sessionId("cs_fail_123")
                .status(PaymentStatus.PROCESSING)
                .build();

        doReturn(event)
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(CheckoutSessionWebhookEvent.class));
        when(orderRepository.findBySessionId("cs_fail_123")).thenReturn(Optional.of(order));
        when(paymentService.findBySessionId("cs_fail_123")).thenReturn(payment);
        doNothing().when(paymentService).recordFailEnding(
                eq(payment),
                eq(PaymentStatus.FAILED),
                eq(BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED.toString())
        );

        assertDoesNotThrow(() -> checkoutSessionWebhookService.handleCheckoutSessionAsyncPaymentFailed(ctx));

        assertEquals("order_456", ctx.getTrackingId());
        assertEquals("cs_fail_123", ctx.getProviderTrackingId());
        assertEquals(OrderStatus.PAYMENT_FAILED_RETRYABLE, order.getStatus());
        assertNull(order.getActiveKey());
        verify(paymentService).recordFailEnding(
                eq(payment),
                eq(PaymentStatus.FAILED),
                eq(BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED.toString())
        );
        verify(paymentRepository).save(payment);
    }
}
