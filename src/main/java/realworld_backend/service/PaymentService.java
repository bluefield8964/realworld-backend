package realworld_backend.service;

import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import realworld_backend.model.Order;
import realworld_backend.repository.OrderRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final OrderRepository orderRepository;

    public PaymentService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }


    public String createPayment(Jwt jwt) throws Exception {

        Long userId = jwt.getClaim("userId");

        // 1. CHECK IF USER ALREADY HAS ACTIVE ORDER (VERY IMPORTANT)
        Optional<Order> existingActive =
                orderRepository.findByUserIdAndStatus(userId, "PENDING");

        if (existingActive.isPresent()) {
            return existingActive.get().getPaymentUrl();
        }

        // 2. CREATE NEW ORDER
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNo(UUID.randomUUID().toString());
        order.setAmount(1000L);
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.now());

        orderRepository.save(order);

        // 3. ATOMIC LOCK (PREVENT RACE CONDITION)
        int updated = orderRepository.lockOrder(order.getOrderNo());

        if (updated == 0) {
            // another request already locked it
            Order lockedOrder = orderRepository.findByOrderNo(order.getOrderNo())
                    .orElseThrow();

            return lockedOrder.getPaymentUrl();
        }
        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl("http://localhost:3000/success")
                        .setCancelUrl("http://localhost:3000/cancel")
                        .putMetadata("orderNo", order.getOrderNo()) //session process verify
                        .setPaymentIntentData(                      //payment process verify
                                SessionCreateParams.PaymentIntentData.builder()
                                        .putMetadata("orderNo", order.getOrderNo())
                                        .build()
                        )
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
                                                        )
                                                        .build()
                                        )
                                        .build()
                        )
                        .build();

        //Session.create(params,options) will send a request to stripe server,it is a Nio network transport,Session.create could be repeated
        //setIdempotencyKey make sure only one session will exist while using the  Session.create(params,options)
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(order.getOrderNo())
                .build();
        Session session = Session.create(params,options);

        // 5. SAVE SESSION INFO
        order.setStripeSessionId(session.getId());
        order.setPaymentUrl(session.getUrl());
        order.setStatus("PENDING");

        orderRepository.save(order);

        return session.getUrl();
    }
}


