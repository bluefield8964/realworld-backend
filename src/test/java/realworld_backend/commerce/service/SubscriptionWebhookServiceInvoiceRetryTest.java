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
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.service.core.WebhookContext;
import realworld_backend.commerce.service.impl.parser.WebhookObjectParserRouter;
import realworld_backend.commerce.service.subscription.CustomerSubscriptionService;
import realworld_backend.commerce.service.subscription.SubscriptionHistoryService;
import realworld_backend.commerce.service.subscription.SubscriptionWebhookService;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionWebhookServiceInvoiceRetryTest {

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
    void invoicePaymentFailedMissingMetadataShouldReturnJsonErrorBeforeThreshold() {
        ProviderRawEvent rowEvent = buildInvoicePaymentFailedRowEventWithoutMetadata();

        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_invoice_fail_1")
                .eventType(BusinessEventType.INVOICE_PAYMENT_FAILED)
                .providerRawEvent(rowEvent)
                .attempts(0)
                .build();

        doReturn(buildInvoicePaymentFailedEventWithoutMetadata())
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(InvoiceWebhookEvent.class));

        BizException exception = assertThrows(BizException.class, () -> subscriptionWebhookService.handleInvoicePaymentFailed(ctx));
        assertEquals(ErrorCode.JSON_ERROR, exception.getErrorCode());
    }

    @Test
    void invoicePaymentFailedMissingMetadataShouldFallbackLookupAfterThreshold() {
        ProviderRawEvent rowEvent = buildInvoicePaymentFailedRowEventWithoutMetadata();

        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_invoice_fail_2")
                .eventType(BusinessEventType.INVOICE_PAYMENT_FAILED)
                .providerRawEvent(rowEvent)
                .attempts(5)
                .build();

        doReturn(buildInvoicePaymentFailedEventWithoutMetadata())
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(InvoiceWebhookEvent.class));

        CustomerSubscription customerSubscription = CustomerSubscription.builder()
                .subscriptionNo("sub_no_123")
                .build();
        when(customerSubscriptionService.findByProviderSubscriptionId("sub_123"))
                .thenReturn(Optional.of(customerSubscription));
        when(valueOperations.get(anyString())).thenReturn("idempotent_hit");

        assertDoesNotThrow(() -> subscriptionWebhookService.handleInvoicePaymentFailed(ctx));
        assertEquals("sub_no_123", ctx.getTrackingId());
        assertEquals("in_123", ctx.getProviderTrackingId());
    }

    @Test
    void invoicePaymentFailedMissingMetadataShouldUpsertAbnormalWithInvoiceIdAsSessionId() {
        ProviderRawEvent rowEvent = buildInvoicePaymentFailedRowEventWithoutMetadata();

        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_invoice_fail_3")
                .eventType(BusinessEventType.INVOICE_PAYMENT_FAILED)
                .providerRawEvent(rowEvent)
                .attempts(5)
                .build();

        doReturn(buildInvoicePaymentFailedEventWithoutMetadata())
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(InvoiceWebhookEvent.class));

        BizException exception = assertThrows(BizException.class, () -> subscriptionWebhookService.handleInvoicePaymentFailed(ctx));

        assertEquals(ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND, exception.getErrorCode());
        verify(abnormalOrchestrator).upsertAbnormalOrder(
                eq(null),
                eq("evt_invoice_fail_3"),
                eq(BusinessEventType.INVOICE_PAYMENT_FAILED),
                eq("in_123"),
                eq("handleInvoice_CUSTOMER_SUBSCRIPTION_MISSING"),
                eq("CUSTOMER_SUBSCRIPTION_MISSING"),
                eq(AbnormalOrderType.SUBSCRIPTION_MISSING),
                eq("STRIPE"),
                eq(null)
        );
    }

    @Test
    void invoicePaymentFailedShouldOnlyMoveSubscriptionToPastDue() throws Exception {
        ProviderRawEvent rowEvent = ProviderRawEvent.builder()
                .provider("STRIPE")
                .eventId("evt_invoice_fail_4")
                .type(BusinessEventType.INVOICE_PAYMENT_FAILED)
                .rawType("invoice.payment_failed")
                .rawObjectJson("{}")
                .created(1715000000L)
                .livemode(false)
                .build();

        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_invoice_fail_4")
                .eventType(BusinessEventType.INVOICE_PAYMENT_FAILED)
                .providerRawEvent(rowEvent)
                .attempts(0)
                .build();

        InvoiceWebhookEvent event = buildInvoicePaymentFailedEventWithMetadata();
        doReturn(event)
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(InvoiceWebhookEvent.class));
        when(valueOperations.get(anyString())).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(customerSubscriptionService.findBySubscriptionNo("sub_no_123"))
                .thenReturn(Optional.of(CustomerSubscription.builder().subscriptionNo("sub_no_123").build()));

        assertDoesNotThrow(() -> subscriptionWebhookService.handleInvoicePaymentFailed(ctx));

        verify(invoiceService).upsertInvoiceByEvent(event, "sub_no_123", PaymentStatus.FAILED);
        verify(customerSubscriptionService).updateStatusToPastDue(eq("sub_no_123"), any());
        assertEquals("sub_no_123", ctx.getTrackingId());
        assertEquals("in_123", ctx.getProviderTrackingId());
    }

    @Test
    void invoicePaymentSucceededShouldOnlyRecoverSubscriptionFromRecoverableStates() throws Exception {
        ProviderRawEvent rowEvent = ProviderRawEvent.builder()
                .provider("STRIPE")
                .eventId("evt_invoice_paid_1")
                .type(BusinessEventType.INVOICE_PAYMENT_SUCCEEDED)
                .rawType("invoice.payment_succeeded")
                .rawObjectJson("{}")
                .created(1715000000L)
                .livemode(false)
                .build();

        WebhookContext ctx = WebhookContext.builder()
                .provider("STRIPE")
                .eventId("evt_invoice_paid_1")
                .eventType(BusinessEventType.INVOICE_PAYMENT_SUCCEEDED)
                .providerRawEvent(rowEvent)
                .attempts(0)
                .build();

        InvoiceWebhookEvent event = buildInvoicePaymentSucceededEventWithMetadata();
        doReturn(event)
                .when(webhookObjectParserRouter)
                .parseAs(any(), any(), eq(InvoiceWebhookEvent.class));
        when(valueOperations.get(anyString())).thenReturn(null);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);

        assertDoesNotThrow(() -> subscriptionWebhookService.handleInvoicePaymentSucceeded(ctx));

        verify(invoiceService).upsertInvoiceByEvent(event, "sub_no_123", PaymentStatus.SUCCESS);
        verify(customerSubscriptionService).updateStatusToActiveFromRecoverable(eq("sub_no_123"), any());
        assertEquals("sub_no_123", ctx.getTrackingId());
        assertEquals("in_123", ctx.getProviderTrackingId());
    }

    private ProviderRawEvent buildInvoicePaymentFailedRowEventWithoutMetadata() {
        return ProviderRawEvent.builder()
                .provider("STRIPE")
                .eventId("evt_test_invoice_failed")
                .type(BusinessEventType.INVOICE_PAYMENT_FAILED)
                .rawType("invoice.payment_failed")
                .rawObjectJson("{}")
                .created(1715000000L)
                .livemode(false)
                .build();
    }

    private InvoiceWebhookEvent buildInvoicePaymentFailedEventWithoutMetadata() {
        InvoiceWebhookEvent.PaymentError paymentError = new InvoiceWebhookEvent.PaymentError();
        paymentError.setMessage("card declined");

        InvoiceWebhookEvent.InvoiceObject invoiceObject = new InvoiceWebhookEvent.InvoiceObject();
        invoiceObject.setId("in_123");
        invoiceObject.setStatus("open");
        invoiceObject.setPaid(false);
        invoiceObject.setSubscription("sub_123");
        invoiceObject.setCustomer("cus_123");
        invoiceObject.setLastPaymentError(paymentError);

        return InvoiceWebhookEvent.builder()
                .eventId("evt_test_invoice_failed")
                .eventType(BusinessEventType.INVOICE_PAYMENT_FAILED)
                .provider("STRIPE")
                .created(1715000000L)
                .object(invoiceObject)
                .build();
    }

    private InvoiceWebhookEvent buildInvoicePaymentFailedEventWithMetadata() {
        InvoiceWebhookEvent.PaymentError paymentError = new InvoiceWebhookEvent.PaymentError();
        paymentError.setMessage("card declined");

        InvoiceWebhookEvent.InvoiceObject invoiceObject = new InvoiceWebhookEvent.InvoiceObject();
        invoiceObject.setId("in_123");
        invoiceObject.setStatus("open");
        invoiceObject.setPaid(false);
        invoiceObject.setSubscription("sub_123");
        invoiceObject.setCustomer("cus_123");
        invoiceObject.setMetadata(Map.of("subscriptionNo", "sub_no_123"));
        invoiceObject.setLastPaymentError(paymentError);

        return InvoiceWebhookEvent.builder()
                .eventId("evt_invoice_fail_4")
                .eventType(BusinessEventType.INVOICE_PAYMENT_FAILED)
                .provider("STRIPE")
                .created(1715000000L)
                .object(invoiceObject)
                .build();
    }

    private InvoiceWebhookEvent buildInvoicePaymentSucceededEventWithMetadata() {
        InvoiceWebhookEvent.InvoiceObject invoiceObject = new InvoiceWebhookEvent.InvoiceObject();
        invoiceObject.setId("in_123");
        invoiceObject.setStatus("paid");
        invoiceObject.setPaid(true);
        invoiceObject.setSubscription("sub_123");
        invoiceObject.setCustomer("cus_123");
        invoiceObject.setMetadata(Map.of("subscriptionNo", "sub_no_123"));

        return InvoiceWebhookEvent.builder()
                .eventId("evt_invoice_paid_1")
                .eventType(BusinessEventType.INVOICE_PAYMENT_SUCCEEDED)
                .provider("STRIPE")
                .created(1715000000L)
                .object(invoiceObject)
                .build();
    }
}

