package realworld_backend.service;

import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.model.Order;
import realworld_backend.model.OrderStatus;
import realworld_backend.model.StripeEvent;
import realworld_backend.repository.OrderRepository;
import realworld_backend.repository.PaymentRepository;
import realworld_backend.repository.StripeEventRepository;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class WebhookService {


    public final OrderRepository orderRepository;
    private final StripeEventRepository stripeEventRepository;
    private final PaymentRepository paymentRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    public final RedissonClient redissonClient;

    public WebhookService(RedissonClient redissonClient,RedisTemplate<String, Object> redisTemplate, OrderRepository orderRepository, StripeEventRepository stripeEventRepository, PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.redissonClient= redissonClient;
        this.stripeEventRepository = stripeEventRepository;
        this.paymentRepository = paymentRepository;
        this.redisTemplate = redisTemplate;
    }

    /*{
      "id": "pi_3TLNbYFBxenD80aO01qPh0Aj",
      "object": "payment_intent",
      "status": "succeeded",
      "amount": 1000,
      "amount_received": 1000,
      "amount_capturable": 0,
      "currency": "usd",
      "livemode": false,
      "created": 1775998752,
      "capture_method": "automatic",
      "confirmation_method": "automatic",
      "client_secret": "pi_3TLNbYFBxenD80aO01qPh0Aj_secret_95jS8gxNDCvHpV97ksSWaUh4Y",
      "latest_charge": "py_3TLNbYFBxenD80aO0FXqcG1t",
      "metadata": { "orderNo": "6fa34224-3f7c-4a58-928c-a1e668cf0f58"},
      "payment_method": "pm_1TLNbRFBxenD80aO7ISNwrQO",
      "payment_method_types": ["link"],
      "payment_method_options": {    "link": {"persistent_token": null  }  },
      "payment_details": {    "order_reference": "cs_test_a1QoSBepqHJviDsClTrzhEQ40TGVn1D90VpVWlYRSIXgixxBeceYU2I1ab",   "customer_reference": null},
      "amount_details": {
        "shipping": {     "amount": 0,      "from_postal_code": null,      "to_postal_code": null    },
        "tax": {      "total_tax_amount": 0    },
        "tip": {}
      }
    }*/
    public void handleStripeEvent(String payload, String sigHeader, String secret) throws Exception {

        Event event = Webhook.constructEvent(payload, sigHeader, secret);
        String type = event.getType();
        String eventId = event.getId();
        log.info("EVENT: {}", type);
        log.info("RAW: {}", event.getDataObjectDeserializer().getRawJson());


        // 🔥 idempotency
        try {
            stripeEventRepository.save(
                    new StripeEvent(eventId, event.getType(), event.getCreated())
            );
        } catch (DuplicateKeyException e) {
            log.info("event insert fail , eventId: {},eventType: {}", eventId, event.getType());
            return; // handled
        }
        switch (type) {
            case "checkout.session.completed":

                handleCheckoutSessionCompleted(event);
            case "payment_intent.created":
                log.info("PaymentIntent built: {}", type);
                break;
            case "payment_intent.succeeded":

                log.info("customer finish and paid the payment: {}", eventId);
                break;
            case "payment_intent.payment_failed":

                PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow();

                String failOrderNo = intent.getMetadata().get("orderNo");

                if (failOrderNo != null) {
                    handlePaymentFail(failOrderNo);
                }

                log.info("payment failed: {}", failOrderNo);
                break;

        }
    }

    private void handlePaymentFail(String failOrderNo) {
    }


    // critical module
    @Transactional
    public void handlePaymentSessionSuccess(String eventId, String sessionId) {

        // ========= 1. 幂等校验 =========
        String idempotentKey = "stripe:event:" + eventId;

        RLock lock = redissonClient.getLock("session:lock:" + sessionId);
        try {
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", Duration.ofHours(24));

        if (Boolean.FALSE.equals(firstTime)) {
            // 已处理过
            return;
        }

        // ========= 2. 分布式锁 =========
            lock.lock();

            // ========= 3. 查订单 =========
            Optional<Order> optionalOrder =
                    orderRepository.findByStripeSessionId(sessionId);

            if (optionalOrder.isEmpty()) {
                return;
            }

            Order order = optionalOrder.get();

            // ========= 4. 幂等（业务层） =========
            if ("SUCCESS".equals(order.getStatus())) {
                return;
            }

            // ========= 5. 更新状态 =========
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
            log.info("✅ webhookSession success: {}", sessionId);

        } finally {
            // ========= 6. 释放锁 =========
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


    private void handleCheckoutSessionCompleted(Event event) {

        try {
            // ✅ 1. 用 Stripe 官方反序列化
                String json = event.getData().getObject().toJson();
                Session session = Session.GSON.fromJson(json, Session.class);
                log.info("generated by getDataObjectDeserializer() fail,using GSON, session json: {}", json);
                if (session == null) return;


            // ✅ 2. 获取基础字段
            String sessionId = session.getId();
            String paymentStatus = session.getPaymentStatus();

            if (!"paid".equals(paymentStatus)) {
                log.warn("❌ payment not completed, status={}", paymentStatus);
                return;
            }

            // ✅ 3. metadata（你的核心业务字段）
            Map<String, String> metadata = session.getMetadata();

            if (metadata == null || !metadata.containsKey("orderNo")) {
                log.error("❌ orderNo missing in metadata");
                return;
            }

            String orderNo = metadata.get("orderNo");

            // ✅ 4. paymentIntent
            String paymentIntent = session.getPaymentIntent();

            // ✅ 5. 金额（注意单位：分）
            Long amount = session.getAmountTotal();

            log.info("✅ Payment success: orderNo={}, amount={}, pi={}, sessionId={}",
                    orderNo, amount, paymentIntent, sessionId);

            // ✅ 6. use the core module（ sessionId + eventId） OrderNo doesn't work in webhook process
            handlePaymentSessionSuccess(event.getId(), sessionId);

        } catch (Exception e) {
            log.error("❌ parse session failed", e);
        }
    }
}
