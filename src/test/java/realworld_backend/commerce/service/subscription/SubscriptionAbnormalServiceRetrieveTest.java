package realworld_backend.commerce.service.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import realworld_backend.commerce.model.log.AbnormalOrder;
import realworld_backend.commerce.model.log.AbnormalOrderStatus;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.model.core.ProviderInvoice;
import realworld_backend.commerce.model.core.ProviderSubscription;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.enums.ProviderType;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.repository.AbnormalOrderRepository;
import realworld_backend.commerce.service.InvoiceService;
import realworld_backend.commerce.service.core.PaymentChannel;
import realworld_backend.commerce.service.core.PaymentChannelRouter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionAbnormalServiceRetrieveTest {

    @Mock
    private AbnormalOrderRepository abnormalOrderRepository;
    @Mock
    private CustomerSubscriptionService customerSubscriptionService;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private PaymentChannelRouter paymentChannelRouter;
    @Mock
    private PaymentChannel paymentChannel;

    private SubscriptionAbnormalService subscriptionAbnormalService;

    @BeforeEach
    void setUp() {
        subscriptionAbnormalService = new SubscriptionAbnormalService(
                abnormalOrderRepository,
                customerSubscriptionService,
                invoiceService,
                paymentChannelRouter
        );
    }

    @Test
    void reconcileSubscriptionShouldRetrieveProviderSubscriptionBySubscriptionId() {
        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_123")
                .provider(ProviderType.STRIPE)
                .providerSubscriptionId("sub_123")
                .currentPeriodStart(LocalDateTime.now().minusDays(30))
                .currentPeriodEnd(LocalDateTime.now().plusDays(30))
                .cancelAtPeriodEnd(false)
                .status(SubscriptionStatus.PENDING)
                .updatedAt(LocalDateTime.now())
                .build();
        ProviderSubscription providerSubscription = ProviderSubscription.builder()
                .id("sub_123")
                .object("subscription")
                .customer("cus_123")
                .status("active")
                .currentPeriodStart(1715000000L)
                .currentPeriodEnd(1717600000L)
                .cancelAtPeriodEnd(false)
                .build();
        AbnormalOrder retryCandidate = AbnormalOrder.builder()
                .provider("STRIPE")
                .orderNo("sub_no_123")
                .sessionId("sub_123")
                .abnormalType(AbnormalOrderType.SUBSCRIPTION_MISSING)
                .status(AbnormalOrderStatus.PENDING)
                .retryCount(0)
                .build();

        when(customerSubscriptionService.findBySubscriptionNo("sub_no_123")).thenReturn(Optional.of(local));
        when(paymentChannelRouter.get("STRIPE")).thenReturn(paymentChannel);
        when(paymentChannel.retrieveSubscription("sub_123")).thenReturn(providerSubscription);
        when(abnormalOrderRepository.updateStatusToReconcilingBySessionId(
                eq("sub_123"),
                eq(AbnormalOrderStatus.RECONCILING),
                any(),
                any()))
                .thenReturn(1);

        subscriptionAbnormalService.reconcileSubscription(retryCandidate);

        verify(paymentChannel).retrieveSubscription("sub_123");
        verify(paymentChannel, never()).retrieveSession(anyString());
        verify(abnormalOrderRepository).updateStatusToReconcilingBySessionId(
                eq("sub_123"),
                eq(AbnormalOrderStatus.RECONCILING),
                any(),
                any());
        verify(customerSubscriptionService).save(local);
        verify(abnormalOrderRepository).save(retryCandidate);
    }

    @Test
    void reconcileInvoiceShouldRetrieveProviderInvoiceByInvoiceId() {
        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_123")
                .provider(ProviderType.STRIPE)
                .providerSubscriptionId("sub_123")
                .providerCustomerId("cus_123")
                .cancelAtPeriodEnd(false)
                .status(SubscriptionStatus.ACTIVE)
                .updatedAt(LocalDateTime.now())
                .build();
        ProviderInvoice providerInvoice = ProviderInvoice.builder()
                .id("in_123")
                .object("invoice")
                .subscription("sub_123")
                .customer("cus_123")
                .status("paid")
                .paid(true)
                .amountDue(1000L)
                .amountPaid(1000L)
                .amountRemaining(0L)
                .currency("usd")
                .build();
        AbnormalOrder retryCandidate = AbnormalOrder.builder()
                .provider("STRIPE")
                .orderNo("sub_no_123")
                .sessionId("in_123")
                .abnormalType(AbnormalOrderType.SUBSCRIPTION_MISSING)
                .status(AbnormalOrderStatus.PENDING)
                .retryCount(0)
                .build();

        when(paymentChannelRouter.get("STRIPE")).thenReturn(paymentChannel);
        when(paymentChannel.retrieveInvoice("in_123")).thenReturn(providerInvoice);
        when(customerSubscriptionService.findBySubscriptionNo("sub_no_123")).thenReturn(Optional.of(local));
        when(abnormalOrderRepository.updateStatusToReconcilingBySessionId(
                eq("in_123"),
                eq(AbnormalOrderStatus.RECONCILING),
                any(),
                any()))
                .thenReturn(1);

        subscriptionAbnormalService.reconcileInvoice(retryCandidate);

        verify(paymentChannel).retrieveInvoice("in_123");
        verify(paymentChannel, never()).retrieveSession(anyString());
        verify(abnormalOrderRepository).updateStatusToReconcilingBySessionId(
                eq("in_123"),
                eq(AbnormalOrderStatus.RECONCILING),
                any(),
                any());
        verify(invoiceService).upsertInvoiceByRetrieve("STRIPE", providerInvoice, "sub_no_123");
        verify(abnormalOrderRepository).save(retryCandidate);
    }

    @Test
    void reconcileInvoiceShouldRestoreInvoiceEvenWhenLocalSubscriptionIsMissing() {
        ProviderInvoice providerInvoice = ProviderInvoice.builder()
                .id("in_123")
                .object("invoice")
                .subscription("sub_123")
                .customer("cus_123")
                .status("paid")
                .paid(true)
                .amountDue(1000L)
                .amountPaid(1000L)
                .amountRemaining(0L)
                .currency("usd")
                .build();
        AbnormalOrder retryCandidate = AbnormalOrder.builder()
                .provider("STRIPE")
                .orderNo("sub_no_123")
                .sessionId("in_123")
                .abnormalType(AbnormalOrderType.SUBSCRIPTION_MISSING)
                .status(AbnormalOrderStatus.PENDING)
                .retryCount(0)
                .build();

        when(paymentChannelRouter.get("STRIPE")).thenReturn(paymentChannel);
        when(paymentChannel.retrieveInvoice("in_123")).thenReturn(providerInvoice);
        when(customerSubscriptionService.findBySubscriptionNo("sub_no_123")).thenReturn(Optional.empty());
        when(customerSubscriptionService.findByProviderSubscriptionId("sub_123")).thenReturn(Optional.empty());
        when(customerSubscriptionService.findByProviderCustomerId("cus_123")).thenReturn(Optional.empty());
        when(abnormalOrderRepository.updateStatusToReconcilingBySessionId(
                eq("in_123"),
                eq(AbnormalOrderStatus.RECONCILING),
                any(),
                any()))
                .thenReturn(1);

        subscriptionAbnormalService.reconcileInvoice(retryCandidate);

        verify(invoiceService).upsertInvoiceByRetrieve("STRIPE", providerInvoice, "sub_no_123");
        verify(abnormalOrderRepository).save(retryCandidate);
    }

    @Test
    void reconcileInvoiceShouldUseProviderMetadataBusinessOrderNoWhenTrackedOrderNoIsMissing() {
        ProviderInvoice providerInvoice = ProviderInvoice.builder()
                .id("in_123")
                .object("invoice")
                .subscription("sub_123")
                .customer("cus_123")
                .status("paid")
                .paid(true)
                .metadata(Map.of("subscriptionNo", "sub_no_123"))
                .build();
        AbnormalOrder retryCandidate = AbnormalOrder.builder()
                .provider("STRIPE")
                .orderNo(null)
                .sessionId("in_123")
                .abnormalType(AbnormalOrderType.SUBSCRIPTION_MISSING)
                .status(AbnormalOrderStatus.PENDING)
                .retryCount(0)
                .build();

        when(paymentChannelRouter.get("STRIPE")).thenReturn(paymentChannel);
        when(paymentChannel.retrieveInvoice("in_123")).thenReturn(providerInvoice);
        when(customerSubscriptionService.findByProviderSubscriptionId("sub_123")).thenReturn(Optional.empty());
        when(customerSubscriptionService.findByProviderCustomerId("cus_123")).thenReturn(Optional.empty());
        when(abnormalOrderRepository.updateStatusToReconcilingBySessionId(
                eq("in_123"),
                eq(AbnormalOrderStatus.RECONCILING),
                any(),
                any()))
                .thenReturn(1);

        subscriptionAbnormalService.reconcileInvoice(retryCandidate);

        verify(invoiceService).upsertInvoiceByRetrieve("STRIPE", providerInvoice, "sub_no_123");
        verify(abnormalOrderRepository).save(retryCandidate);
    }

    @Test
    void reconcileInvoiceShouldUseResolvedLocalBusinessOrderNoWhenTrackedOrderNoIsMissing() {
        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_123")
                .provider(ProviderType.STRIPE)
                .providerSubscriptionId("sub_123")
                .providerCustomerId("cus_123")
                .status(SubscriptionStatus.ACTIVE)
                .updatedAt(LocalDateTime.now())
                .build();
        ProviderInvoice providerInvoice = ProviderInvoice.builder()
                .id("in_123")
                .object("invoice")
                .subscription("sub_123")
                .customer("cus_123")
                .status("paid")
                .paid(true)
                .build();
        AbnormalOrder retryCandidate = AbnormalOrder.builder()
                .provider("STRIPE")
                .orderNo(null)
                .sessionId("in_123")
                .abnormalType(AbnormalOrderType.SUBSCRIPTION_MISSING)
                .status(AbnormalOrderStatus.PENDING)
                .retryCount(0)
                .build();

        when(paymentChannelRouter.get("STRIPE")).thenReturn(paymentChannel);
        when(paymentChannel.retrieveInvoice("in_123")).thenReturn(providerInvoice);
        when(customerSubscriptionService.findByProviderSubscriptionId("sub_123")).thenReturn(Optional.of(local));
        when(abnormalOrderRepository.updateStatusToReconcilingBySessionId(
                eq("in_123"),
                eq(AbnormalOrderStatus.RECONCILING),
                any(),
                any()))
                .thenReturn(1);

        subscriptionAbnormalService.reconcileInvoice(retryCandidate);

        verify(invoiceService).upsertInvoiceByRetrieve("STRIPE", providerInvoice, "sub_no_123");
        verify(abnormalOrderRepository).save(retryCandidate);
    }

    @Test
    void reconcileSubscriptionShouldContinueWhenStaleReconcilingKeepsAbnormalType() {
        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_123")
                .provider(ProviderType.STRIPE)
                .providerSubscriptionId("sub_123")
                .currentPeriodStart(LocalDateTime.now().minusDays(30))
                .currentPeriodEnd(LocalDateTime.now().plusDays(30))
                .cancelAtPeriodEnd(false)
                .status(SubscriptionStatus.PENDING)
                .updatedAt(LocalDateTime.now())
                .build();
        ProviderSubscription providerSubscription = ProviderSubscription.builder()
                .id("sub_123")
                .object("subscription")
                .customer("cus_123")
                .status("active")
                .currentPeriodStart(1715000000L)
                .currentPeriodEnd(1717600000L)
                .cancelAtPeriodEnd(false)
                .build();
        AbnormalOrder retryCandidate = AbnormalOrder.builder()
                .provider("STRIPE")
                .orderNo("sub_no_123")
                .sessionId("sub_123")
                .abnormalType(AbnormalOrderType.SUBSCRIPTION_MISSING)
                .status(AbnormalOrderStatus.RECONCILING)
                .retryCount(0)
                .build();

        when(customerSubscriptionService.findBySubscriptionNo("sub_no_123")).thenReturn(Optional.of(local));
        when(paymentChannelRouter.get("STRIPE")).thenReturn(paymentChannel);
        when(paymentChannel.retrieveSubscription("sub_123")).thenReturn(providerSubscription);
        when(abnormalOrderRepository.updateStatusToReconcilingBySessionId(
                eq("sub_123"),
                eq(AbnormalOrderStatus.RECONCILING),
                any(),
                any()))
                .thenReturn(1);

        subscriptionAbnormalService.reconcileSubscription(retryCandidate);

        verify(paymentChannel).retrieveSubscription("sub_123");
        verify(customerSubscriptionService).save(local);
        verify(abnormalOrderRepository).save(retryCandidate);
    }

    @Test
    void reconcileInvoiceShouldContinueWhenStaleReconcilingKeepsAbnormalType() {
        ProviderInvoice providerInvoice = ProviderInvoice.builder()
                .id("in_123")
                .object("invoice")
                .subscription("sub_123")
                .customer("cus_123")
                .status("paid")
                .paid(true)
                .amountDue(1000L)
                .amountPaid(1000L)
                .amountRemaining(0L)
                .currency("usd")
                .build();
        AbnormalOrder retryCandidate = AbnormalOrder.builder()
                .provider("STRIPE")
                .orderNo("sub_no_123")
                .sessionId("in_123")
                .abnormalType(AbnormalOrderType.SUBSCRIPTION_MISSING)
                .status(AbnormalOrderStatus.RECONCILING)
                .retryCount(0)
                .build();

        when(paymentChannelRouter.get("STRIPE")).thenReturn(paymentChannel);
        when(paymentChannel.retrieveInvoice("in_123")).thenReturn(providerInvoice);
        when(customerSubscriptionService.findBySubscriptionNo("sub_no_123")).thenReturn(Optional.empty());
        when(customerSubscriptionService.findByProviderSubscriptionId("sub_123")).thenReturn(Optional.empty());
        when(customerSubscriptionService.findByProviderCustomerId("cus_123")).thenReturn(Optional.empty());
        when(abnormalOrderRepository.updateStatusToReconcilingBySessionId(
                eq("in_123"),
                eq(AbnormalOrderStatus.RECONCILING),
                any(),
                any()))
                .thenReturn(1);

        subscriptionAbnormalService.reconcileInvoice(retryCandidate);

        verify(paymentChannel).retrieveInvoice("in_123");
        verify(invoiceService).upsertInvoiceByRetrieve("STRIPE", providerInvoice, "sub_no_123");
        verify(abnormalOrderRepository).save(retryCandidate);
    }

    @Test
    void reconcileSubscriptionShouldStopWhenAnotherThreadIsReconciling() {
        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_123")
                .provider(ProviderType.STRIPE)
                .providerSubscriptionId("sub_123")
                .status(SubscriptionStatus.PENDING)
                .updatedAt(LocalDateTime.now())
                .build();
        ProviderSubscription providerSubscription = ProviderSubscription.builder()
                .id("sub_123")
                .status("active")
                .build();
        AbnormalOrder retryCandidate = AbnormalOrder.builder()
                .provider("STRIPE")
                .orderNo("sub_no_123")
                .sessionId("sub_123")
                .abnormalType(AbnormalOrderType.SUBSCRIPTION_MISSING)
                .status(AbnormalOrderStatus.PENDING)
                .retryCount(0)
                .build();

        when(customerSubscriptionService.findBySubscriptionNo("sub_no_123")).thenReturn(Optional.of(local));
        when(abnormalOrderRepository.updateStatusToReconcilingBySessionId(
                eq("sub_123"),
                eq(AbnormalOrderStatus.RECONCILING),
                any(),
                any()))
                .thenReturn(0);

        subscriptionAbnormalService.reconcileSubscription(retryCandidate);

        verify(paymentChannel, never()).retrieveSubscription(anyString());
        verify(abnormalOrderRepository, never())
                .updateStatusBySessionId(eq("sub_123"), eq(AbnormalOrderStatus.RECONCILING), any());
        verify(customerSubscriptionService, never()).save(any());
        verify(abnormalOrderRepository, never()).save(retryCandidate);
    }

    @Test
    void reconcileInvoiceShouldStopWhenAnotherThreadIsReconciling() {
        ProviderInvoice providerInvoice = ProviderInvoice.builder()
                .id("in_123")
                .subscription("sub_123")
                .customer("cus_123")
                .status("paid")
                .paid(true)
                .build();
        AbnormalOrder retryCandidate = AbnormalOrder.builder()
                .provider("STRIPE")
                .orderNo("sub_no_123")
                .sessionId("in_123")
                .abnormalType(AbnormalOrderType.SUBSCRIPTION_MISSING)
                .status(AbnormalOrderStatus.PENDING)
                .retryCount(0)
                .build();

        when(abnormalOrderRepository.updateStatusToReconcilingBySessionId(
                eq("in_123"),
                eq(AbnormalOrderStatus.RECONCILING),
                any(),
                any()))
                .thenReturn(0);

        subscriptionAbnormalService.reconcileInvoice(retryCandidate);

        verify(paymentChannel, never()).retrieveInvoice(anyString());
        verify(abnormalOrderRepository, never())
                .updateStatusBySessionId(eq("in_123"), eq(AbnormalOrderStatus.RECONCILING), any());
        verify(invoiceService, never()).upsertInvoiceByRetrieve(anyString(), any(), anyString());
        verify(abnormalOrderRepository, never()).save(retryCandidate);
    }

    @Test
    void reconcileSubscriptionShouldMarkManualReviewWhenProviderSubscriptionIdMismatches() {
        CustomerSubscription local = CustomerSubscription.builder()
                .subscriptionNo("sub_no_123")
                .provider(ProviderType.STRIPE)
                .providerSubscriptionId("sub_123")
                .status(SubscriptionStatus.PENDING)
                .updatedAt(LocalDateTime.now())
                .build();
        ProviderSubscription providerSubscription = ProviderSubscription.builder()
                .id("sub_other")
                .object("subscription")
                .status("active")
                .customer("cus_123")
                .build();
        AbnormalOrder retryCandidate = AbnormalOrder.builder()
                .provider("STRIPE")
                .orderNo("sub_no_123")
                .sessionId("sub_123")
                .abnormalType(AbnormalOrderType.SUBSCRIPTION_MISSING)
                .status(AbnormalOrderStatus.PENDING)
                .retryCount(0)
                .build();

        when(customerSubscriptionService.findBySubscriptionNo("sub_no_123")).thenReturn(Optional.of(local));
        when(paymentChannelRouter.get("STRIPE")).thenReturn(paymentChannel);
        when(paymentChannel.retrieveSubscription("sub_123")).thenReturn(providerSubscription);
        when(abnormalOrderRepository.updateStatusToReconcilingBySessionId(
                eq("sub_123"),
                eq(AbnormalOrderStatus.RECONCILING),
                any(),
                any()))
                .thenReturn(1);

        subscriptionAbnormalService.reconcileSubscription(retryCandidate);

        verify(customerSubscriptionService, never()).save(any());
        verify(abnormalOrderRepository).save(retryCandidate);
    }

    @Test
    void reconcileInvoiceShouldMarkManualReviewWhenProviderInvoiceIdMismatches() {
        ProviderInvoice providerInvoice = ProviderInvoice.builder()
                .id("in_other")
                .object("invoice")
                .subscription("sub_123")
                .customer("cus_123")
                .status("paid")
                .paid(true)
                .build();
        AbnormalOrder retryCandidate = AbnormalOrder.builder()
                .provider("STRIPE")
                .orderNo("sub_no_123")
                .sessionId("in_123")
                .abnormalType(AbnormalOrderType.SUBSCRIPTION_MISSING)
                .status(AbnormalOrderStatus.PENDING)
                .retryCount(0)
                .build();

        when(paymentChannelRouter.get("STRIPE")).thenReturn(paymentChannel);
        when(paymentChannel.retrieveInvoice("in_123")).thenReturn(providerInvoice);
        when(abnormalOrderRepository.updateStatusToReconcilingBySessionId(
                eq("in_123"),
                eq(AbnormalOrderStatus.RECONCILING),
                any(),
                any()))
                .thenReturn(1);

        subscriptionAbnormalService.reconcileInvoice(retryCandidate);

        verify(invoiceService, never()).upsertInvoiceByRetrieve(anyString(), any(), anyString());
        verify(abnormalOrderRepository).save(retryCandidate);
    }
}
