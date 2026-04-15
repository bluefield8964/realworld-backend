package realworld_backend.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.dto.Exception.BizException;
import realworld_backend.dto.Exception.ErrorCode;
import realworld_backend.model.Order;
import realworld_backend.model.OrderStatus;
import realworld_backend.repository.OrderRepository;
import realworld_backend.repository.PaymentRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PaymentService paymentService;
    private final Executor paymentExecutor;

    public OrderService(RedisTemplate<String, Object> redisTemplate, OrderRepository orderRepository, PaymentRepository paymentRepository, PaymentService paymentService, Executor paymentExecutor) {
        this.orderRepository = orderRepository;
        this.redisTemplate = redisTemplate;
        this.paymentService = paymentService;
        this.paymentExecutor = paymentExecutor;
    }

    public String orderProcess(Jwt jwt, Long productId) throws Exception {
        Long userId = jwt.getClaim("userId");
        String activeKey = buildActiveKey(userId, productId);
        Order order = null;
        Session stripeSession = null;
        String lockKey = "pay:lock:" + activeKey;
        try {
            //prevent duplicate generation ---redis lock
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
            if (!success) {
                throw new BizException(ErrorCode.ORDER_ALREADY_CREATED);
            }

            //already prevent duplicating generate order
            order = createOrder(userId, productId);
            String orderNo = order.getOrderNo();
            CompletableFuture.runAsync(() -> paymentService.recordInit(orderNo), paymentExecutor)
                    .exceptionally(e -> {
                        log.error("payment async error", e);
                        return null;
                    });
            if (order.getStatus() == OrderStatus.PENDING) { //prevent gaining the PENDING status order
                return order.getPaymentUrl();
            }

            try {
                stripeSession = createStripeSession(order);

            } catch (StripeException e) {
                saveFailOrder(order, e);

                CompletableFuture.runAsync(() -> paymentService.recordFail(orderNo, e), paymentExecutor)
                        .exceptionally(a -> {
                            log.error("payment async error", a);
                            return null;
                        });

                throw e;
            }
            String sessionId = stripeSession.getId();
            String url = saveSuccessOrder(order, stripeSession);

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
        //selecting generated order     ---database unique lock
        Optional<Order> existingOrder = orderRepository.findByActiveKey(activeKey);
        if (existingOrder.isPresent() && existingOrder.get().getStatus() == OrderStatus.PENDING) {
            return existingOrder.get();
        }
        // CREATE NEW ORDER , MAKE A ATOMIC LOCK (PREVENT RACE CONDITION)
        Order order = new Order();
        order.setActiveKey(activeKey);
        order.setStatus(OrderStatus.CREATED);
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setAmount(1000L);
        order.setProduct(520L);
        order.setStripeSessionId("");
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
                        .putMetadata("orderNo", order.getOrderNo()) //session process verify
                        .setPaymentIntentData(                      //payment process verify
                                SessionCreateParams.PaymentIntentData.builder().putMetadata("orderNo", order.getOrderNo()).build())
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
        //Session.create(params,options) will send a request to stripe server,it is a Nio network transport,Session.create could be repeated
        //setIdempotencyKey make sure only one session will exist while using the  Session.create(params,options)
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(order.getOrderNo())
                .build();
        //after this , maybe stripe will send multipart response , so need to verify the status
        return Session.create(params, options);
    }

    @Transactional
    public String saveSuccessOrder(Order order, Session session) throws Exception {
// if create session successfully，use session.getUrl() and others parameters
        // session.getStatus() normally will be  "open"
        //SAVE SESSION INFO
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
