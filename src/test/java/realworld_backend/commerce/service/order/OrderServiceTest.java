package realworld_backend.commerce.service.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.commerce.model.Order;
import realworld_backend.commerce.model.OrderStatus;
import realworld_backend.commerce.model.core.CheckoutSessionData;
import realworld_backend.commerce.repository.OrderRepository;
import realworld_backend.commerce.service.PaymentService;
import realworld_backend.commerce.service.ProductItemService;
import realworld_backend.commerce.service.core.PaymentChannelRouter;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private PaymentService paymentService;
    @Mock
    private PaymentChannelRouter paymentChannelRouter;
    @Mock
    private ProductItemService productItemService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = spy(new OrderService(
                orderRepository,
                redisTemplate,
                paymentService,
                paymentChannelRouter,
                productItemService
        ));
    }

    @Test
    void createOrReuseOrderCheckoutShouldRecordPaymentSynchronously() throws Exception {
        CurrentAuthUser authUser = new CurrentAuthUser(1L, "sess_1");
        Order order = Order.builder()
                .orderNo("order_123")
                .provider("STRIPE")
                .status(OrderStatus.CREATED)
                .activeKey("1:2")
                .build();
        CheckoutSessionData session = CheckoutSessionData.builder()
                .id("cs_123")
                .url("https://checkout.test")
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        doReturn(order).when(orderService).createOrder(1L, "STRIPE", 2L);
        doReturn(session).when(orderService).createStripeSession(order);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String checkoutUrl = orderService.createOrReuseOrderCheckout(authUser, "STRIPE", 2L);

        assertEquals("https://checkout.test", checkoutUrl);
        InOrder inOrder = inOrder(paymentService);
        inOrder.verify(paymentService).recordInit("order_123", "STRIPE");
        inOrder.verify(paymentService).recordPaying("order_123", "cs_123");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.PENDING, orderCaptor.getValue().getStatus());
        assertEquals("cs_123", orderCaptor.getValue().getSessionId());
    }

    @Test
    void saveFailOrderShouldReleaseActiveKey() throws Exception {
        Order order = Order.builder()
                .orderNo("order_123")
                .status(OrderStatus.CREATED)
                .activeKey("1:2")
                .build();

        orderService.saveFailOrder(order);

        assertEquals(OrderStatus.FAILED, order.getStatus());
        assertNull(order.getActiveKey());
    }
}
