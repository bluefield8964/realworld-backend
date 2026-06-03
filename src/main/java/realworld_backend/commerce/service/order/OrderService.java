package realworld_backend.commerce.service.order;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.commerce.model.Order;
import realworld_backend.commerce.model.OrderStatus;
import realworld_backend.commerce.model.Product;
import realworld_backend.commerce.model.core.CheckoutSessionData;
import realworld_backend.commerce.repository.OrderRepository;
import realworld_backend.commerce.service.PaymentService;
import realworld_backend.commerce.service.ProductItemService;
import realworld_backend.commerce.service.core.PaymentChannel;
import realworld_backend.commerce.service.core.PaymentChannelException;
import realworld_backend.commerce.service.core.PaymentChannelRouter;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Creates payment orders and starts Stripe checkout.
 * Handles duplicate-submit protection and pending-order reuse.
 */
public class OrderService {
    private final OrderRepository orderRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PaymentService paymentService;
    private final PaymentChannelRouter paymentChannelRouter;
    private final ProductItemService productItemService;


    /**
     * Main payment-entry flow from API.
     */

    @Transactional
    public String createOrReuseOrderCheckout(CurrentAuthUser currentUser, String provider, Long productId) throws Exception {
        Long userId = currentUser.userId();
        String activeKey = buildActiveKey(userId, productId);
        Order order = null;
        CheckoutSessionData session = null;
        String lockKey = "pay:lock:" + activeKey;

        try {
            // Short lock to avoid double-click / rapid re-submit.
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
            if (!success) {
                throw new BizException(ErrorCode.ORDER_ALREADY_CREATED);
            }


            // Reuse active pending order; otherwise create a new one.
            order = createOrder(userId, provider, productId);
            String orderNo = order.getOrderNo();

            // Keep same checkout URL for active pending order.
            if (order.getStatus() == OrderStatus.PENDING) {
                return order.getPaymentUrl();
            }
            paymentService.recordInit(orderNo, provider);
            try {
                session = createStripeSession(order);
            } catch (PaymentChannelException e) {
                saveFailOrder(order);
                paymentService.recordFail(orderNo, e);
                throw e;
            }

            String url = saveSuccessOrder(order, session);
            String sessionId = session.getId();
            paymentService.recordPaying(orderNo, sessionId);

            return url;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Transactional
    public Order createOrder(Long userId, String provider, Long productId) {
        String activeKey = buildActiveKey(userId, productId);
        String orderNo = UUID.randomUUID().toString();
        Product product = productItemService.findByProductId(productId);

        // Business-level idempotency: one active checkout slot per user+product.
        Optional<Order> existingOrder = orderRepository.findByActiveKey(activeKey);
        if (existingOrder.isPresent() && existingOrder.get().getStatus() == OrderStatus.PENDING) {
            return existingOrder.get();
        }
        // DB unique key is the last race guard across concurrent workers.
        Order order = Order.builder().createdAt(UtcTimeMapper.nowUtc())
                .orderNo(orderNo)
                .userId(userId)
                .paymentUrl("")
                .provider(provider)
                .amount(product.getPriceAmount())
                .productId(productId)
                .activeKey(activeKey)
                .status(OrderStatus.CREATED).build();

        try {
            orderRepository.save(order);
        } catch (DuplicateKeyException e) {
            return orderRepository.findByActiveKey(activeKey).get();
        }

        return order;
    }

    /**
     * Builds Stripe checkout session and embeds reconciliation metadata.
     */
    public CheckoutSessionData createStripeSession(Order order){
        PaymentChannel paymentChannel = paymentChannelRouter.get(order.getProvider());
        return paymentChannel.createCheckoutSession(order);
    }

    public String saveSuccessOrder(Order order, CheckoutSessionData session) throws Exception {
        // Move order into payable state after checkout URL exists.
        order.setSessionId(session.getId());
        order.setPaymentUrl(session.getUrl());
        order.setStatus(OrderStatus.PENDING);
        orderRepository.save(order);
        return session.getUrl();
    }

    public String saveFailOrder(Order order) throws Exception {
        order.setStatus(OrderStatus.FAILED);
        order.setActiveKey(null);
        orderRepository.save(order);
        return null;
    }

    public String buildActiveKey(Long userId, Long productId) {
        // User+product active-checkout slot key.
        return userId + ":" + productId;
    }

    public Order findBySessionId(String sessionId) {
        Optional<Order> bySessionId = orderRepository.findBySessionId(sessionId);
        if (bySessionId.isPresent()) {
            Order order = bySessionId.get();
            log.info("order:{} is exist", order.getOrderNo());
            return order;
        } else {
            log.info("order:{} not found", sessionId);
            return null;
        }

    }

    public void saveReconcileOrder(Order order) {
        orderRepository.save(order);
    }

    public int markFromStatusToStatus(
            Long id,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            boolean clearActiveKey,
            LocalDateTime now
    ) {
        return orderRepository.markFromStatusToStatus(id, fromStatus, toStatus, clearActiveKey, now);
    }
}


