package realworld_backend.commerce.service.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import realworld_backend.article.service.UserService;
import realworld_backend.commerce.model.log.AbnormalOrder;
import realworld_backend.commerce.model.log.AbnormalOrderStatus;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.model.core.ProviderSession;
import realworld_backend.commerce.repository.AbnormalOrderRepository;
import realworld_backend.commerce.service.PaymentService;
import realworld_backend.commerce.service.ProductItemService;
import realworld_backend.commerce.service.core.PaymentChannel;
import realworld_backend.commerce.service.core.PaymentChannelRouter;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAbnormalServiceValidationTest {

    @Mock
    private AbnormalOrderRepository abnormalOrderRepository;
    @Mock
    private OrderService orderService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private ProductItemService productService;
    @Mock
    private UserService userService;
    @Mock
    private PaymentChannelRouter paymentChannelRouter;
    @Mock
    private PaymentChannel paymentChannel;

    private OrderAbnormalService orderAbnormalService;

    @BeforeEach
    void setUp() {
        orderAbnormalService = new OrderAbnormalService(
                abnormalOrderRepository,
                orderService,
                paymentService,
                productService,
                userService,
                paymentChannelRouter
        );
    }

    @Test
    void reconcileShouldMarkManualReviewWhenProviderMetadataUserIdMissing() throws Exception {
        AbnormalOrder retryCandidate = AbnormalOrder.builder()
                .provider("STRIPE")
                .sessionId("cs_123")
                .abnormalType(AbnormalOrderType.ORDER_MISSING)
                .status(AbnormalOrderStatus.PENDING)
                .retryCount(0)
                .build();
        ProviderSession providerSession = ProviderSession.builder()
                .id("cs_123")
                .status("complete")
                .paymentStatus("paid")
                .amountTotal(1000L)
                .metadata(Map.of(
                        "orderNo", "order_123",
                        "product", "10"
                ))
                .build();

        when(abnormalOrderRepository.updateStatusToReconcilingBySessionId(eq("cs_123"), eq(AbnormalOrderStatus.RECONCILING), any(), any()))
                .thenReturn(1);
        when(paymentChannelRouter.get("STRIPE")).thenReturn(paymentChannel);
        when(paymentChannel.retrieveSession("cs_123")).thenReturn(providerSession);

        orderAbnormalService.reconcile(retryCandidate);

        ArgumentCaptor<AbnormalOrder> captor = ArgumentCaptor.forClass(AbnormalOrder.class);
        verify(abnormalOrderRepository).save(captor.capture());
        AbnormalOrder saved = captor.getValue();
        assertEquals(AbnormalOrderStatus.MANUAL_REVIEW, saved.getStatus());
        assertTrue(saved.getErrorMessage().contains("provider metadata userId missing"));
        verify(orderService, never()).saveReconcileOrder(any());
        verify(paymentService, never()).saveReconcilePayment(any());
    }

    @Test
    void reconcileShouldMarkManualReviewWhenLocalProductResolutionFails() throws Exception {
        AbnormalOrder retryCandidate = AbnormalOrder.builder()
                .provider("STRIPE")
                .sessionId("cs_123")
                .abnormalType(AbnormalOrderType.ORDER_MISSING)
                .status(AbnormalOrderStatus.PENDING)
                .retryCount(0)
                .build();
        ProviderSession providerSession = ProviderSession.builder()
                .id("cs_123")
                .status("complete")
                .paymentStatus("paid")
                .amountTotal(1000L)
                .metadata(Map.of(
                        "orderNo", "order_123",
                        "product", "10",
                        "userId", "20"
                ))
                .build();

        when(abnormalOrderRepository.updateStatusToReconcilingBySessionId(eq("cs_123"), eq(AbnormalOrderStatus.RECONCILING), any(), any()))
                .thenReturn(1);
        when(paymentChannelRouter.get("STRIPE")).thenReturn(paymentChannel);
        when(paymentChannel.retrieveSession("cs_123")).thenReturn(providerSession);
        when(productService.findByProductId(10L)).thenThrow(new BizException(ErrorCode.INVALID_INPUT));

        orderAbnormalService.reconcile(retryCandidate);

        ArgumentCaptor<AbnormalOrder> captor = ArgumentCaptor.forClass(AbnormalOrder.class);
        verify(abnormalOrderRepository).save(captor.capture());
        AbnormalOrder saved = captor.getValue();
        assertEquals(AbnormalOrderStatus.MANUAL_REVIEW, saved.getStatus());
        assertTrue(saved.getErrorMessage().contains("local product resolution failed"));
        verify(orderService, never()).saveReconcileOrder(any());
        verify(paymentService, never()).saveReconcilePayment(any());
    }
}
