package realworld_backend.commerce.service;

import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.commerce.model.Order;
import realworld_backend.commerce.model.OrderStatus;
import realworld_backend.commerce.model.core.CheckoutSessionData;
import realworld_backend.commerce.repository.OrderRepository;
import realworld_backend.commerce.service.core.PaymentChannel;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
    private final Executor paymentExecutor;
    private final PaymentChannel paymentChannel;
    /**
     * Main payment-entry flow from API.
     */
    public String orderProcess(Jwt jwt, Long productId) throws Exception {
        Long userId = jwt.getClaim("userId");
        String activeKey = buildActiveKey(userId, productId);
        Order order = null;
        CheckoutSessionData Session = null;
        String lockKey = "pay:lock:" + activeKey;

        try {
            // Short lock to avoid double-click / rapid re-submit.
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
            if (!success) {
                throw new BizException(ErrorCode.ORDER_ALREADY_CREATED);
            }

            // Reuse active pending order; otherwise create a new one.
            order = createOrder(userId, productId);
            String orderNo = order.getOrderNo();

            // Keep same checkout URL for active pending order.
            if (order.getStatus() == OrderStatus.PENDING) {
                return order.getPaymentUrl();
            }

            CompletableFuture.runAsync(() -> paymentService.recordInit(orderNo), paymentExecutor)
                    .exceptionally(e -> {
                        log.error("payment async error", e);
                        return null;
                    });
            try {
                Session = createStripeSession(order);
            } catch (StripeException e) {
                saveFailOrder(order);

                CompletableFuture.runAsync(() -> paymentService.recordFail(orderNo, e), paymentExecutor)
                        .exceptionally(a -> {
                            log.error("payment async error", a);
                            return null;
                        });

                throw e;
            }

            String url = saveSuccessOrder(order, Session);
            String sessionId = Session.getId();

            CompletableFuture.runAsync(() -> paymentService.recordPaying(orderNo, sessionId), paymentExecutor)
                    .exceptionally(e -> {
                        log.error("payment async error", e);
                        return null;
                    });

            return url;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Transactional
    public Order createOrder(Long userId, Long productId) throws Exception {
        String activeKey = buildActiveKey(userId, productId);
        String orderNo = UUID.randomUUID().toString();

        // Business-level idempotency: one active order per user+product key.
        Optional<Order> existingOrder = orderRepository.findByActiveKey(activeKey);
        if (existingOrder.isPresent() && existingOrder.get().getStatus() == OrderStatus.PENDING) {
            return existingOrder.get();
        }

        // DB unique key is the last race guard across concurrent workers.
        Order order = new Order();
        order.setActiveKey(activeKey);
        order.setStatus(OrderStatus.CREATED);
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setAmount(1000L);
        order.setProductId(520L);
        order.setPaymentUrl("");
        order.setCreatedAt(LocalDateTime.now());

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
    public CheckoutSessionData createStripeSession(Order order) throws Exception {
       /* SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl("http://localhost:3000/success")
                        .setCancelUrl("http://localhost:3000/cancel")
                        .putMetadata("internalCustomerId", order.getUserId().toString())
                        .putMetadata("orderNo", order.getOrderNo())
                        .putMetadata("userId", order.getUserId().toString())
                        .putMetadata("product", String.valueOf(order.getProductId()))
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setQuantity(1L)
                                        .setPriceData(//money
                                                SessionCreateParams.LineItem.PriceData.builder()
                                                        .setCurrency("usd")
                                                        .setUnitAmount(order.getAmount())
                                                        .setProductData(
                                                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                        .setName("VIP Membership")
                                                                        .build()
                                                        ).build()
                                        ).build()
                        ).build();

        // Stripe-side idempotency for network retries.
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(order.getOrderNo())
                .build();*/
        return paymentChannel.createCheckoutSession(order);
    }

    @Transactional
    public String saveSuccessOrder(Order order, CheckoutSessionData session) throws Exception {
        // Move order into payable state after checkout URL exists.
        order.setStripeSessionId(session.getId());
        order.setPaymentUrl(session.getUrl());
        order.setStatus(OrderStatus.PENDING);
        orderRepository.save(order);
        return session.getUrl();
    }

    @Transactional
    public String saveFailOrder(Order order) throws Exception {
        order.setStatus(OrderStatus.FAILED);
        orderRepository.save(order);
        return null;
    }

    public String buildActiveKey(Long userId, Long productId) {
        // User+product active-order key.
        return userId + ":" + productId;
    }

    public Order findBySessionId(String sessionId) {
        Optional<Order> byStripeSessionId = orderRepository.findByStripeSessionId(sessionId);
        if (byStripeSessionId.isPresent()) {
            Order order = byStripeSessionId.get();
            log.info("order:{} is exist", order.getOrderNo());
            return order;
        } else {

            log.info("order:{} not found", sessionId);
            return null;
        }

    }

    public Boolean findBySessionIdAndStatus(String sessionId, OrderStatus orderStatus) {
        return orderRepository.existsByStripeSessionIdAndStatus(sessionId, orderStatus);
    }

    public void saveReconcileOrder(Order order) {
        orderRepository.save(order);
    }
}

