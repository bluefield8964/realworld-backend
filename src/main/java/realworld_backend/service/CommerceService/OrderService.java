package realworld_backend.service.CommerceService;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.dto.Exception.BizException;
import realworld_backend.dto.Exception.ErrorCode;
import realworld_backend.model.commerceModule.Order;
import realworld_backend.model.commerceModule.OrderStatus;
import realworld_backend.repository.OrderRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PaymentService paymentService;
    private final Executor paymentExecutor;

    /**
     * Main order entry:
     * - prevent duplicate submit via Redis lock
     * - create/reuse order
     * - create Stripe session
     * - persist order/payment transitions
     */
    public String orderProcess(Jwt jwt, Long productId) throws Exception {
        Long userId = jwt.getClaim("userId");
        String activeKey = buildActiveKey(userId, productId);
        Order order = null;
        Session stripeSession = null;
        String lockKey = "pay:lock:" + activeKey;

        try {
            // Prevent duplicate generation in short period.
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
            if (!success) {
                throw new BizException(ErrorCode.ORDER_ALREADY_CREATED);
            }

            // Create new order or reuse existing pending order.
            order = createOrder(userId, productId);
            String orderNo = order.getOrderNo();

            if (order.getStatus() == OrderStatus.PENDING) {
                return order.getPaymentUrl();
            }

            CompletableFuture.runAsync(() -> paymentService.recordInit(orderNo), paymentExecutor)
                    .exceptionally(e -> {
                        log.error("payment async error", e);
                        return null;
                    });
            try {
                stripeSession = createStripeSession(order);
            } catch (StripeException e) {
                saveFailOrder(order);

                CompletableFuture.runAsync(() -> paymentService.recordFail(orderNo, e), paymentExecutor)
                        .exceptionally(a -> {
                            log.error("payment async error", a);
                            return null;
                        });

                throw e;
            }

            String url = saveSuccessOrder(order, stripeSession);
            String sessionId = stripeSession.getId();

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

        // Try to reuse pending order first.
        Optional<Order> existingOrder = orderRepository.findByActiveKey(activeKey);
        if (existingOrder.isPresent() && existingOrder.get().getStatus() == OrderStatus.PENDING) {
            return existingOrder.get();
        }

        // Create new order; DB unique key is the final race guard.
        Order order = new Order();
        order.setActiveKey(activeKey);
        order.setStatus(OrderStatus.CREATED);
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setAmount(1000L);
        order.setProduct(520L);
        order.setPaymentUrl("");
        order.setCreatedAt(LocalDateTime.now());

        try {
            orderRepository.save(order);
        } catch (DuplicateKeyException e) {
            return orderRepository.findByActiveKey(activeKey).get();
        }

        return order;
    }

    public Session createStripeSession(Order order) throws StripeException {
        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl("http://localhost:3000/success")
                        .setCancelUrl("http://localhost:3000/cancel")
                        .putMetadata("orderNo", order.getOrderNo())
                        .setPaymentIntentData(
                                SessionCreateParams.PaymentIntentData.builder()
                                        .putMetadata("orderNo", order.getOrderNo())
                                        .build())
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setQuantity(1L)
                                        .setPriceData(
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

        // Idempotency key avoids duplicate Stripe session on network retries.
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(order.getOrderNo())
                .build();

        return Session.create(params, options);
    }

    @Transactional
    public String saveSuccessOrder(Order order, Session session) throws Exception {
        // Persist session data once Stripe session is created.
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
        return userId + ":" + productId;
    }
}
