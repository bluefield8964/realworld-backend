package realworld_backend.service;

import com.stripe.model.checkout.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import realworld_backend.model.Order;
import realworld_backend.model.OrderStatus;
import realworld_backend.repository.OrderRepository;

import java.util.List;

@Slf4j
@Service
@EnableScheduling
public class OrderReconcileService {
    private final OrderRepository orderRepository;

    public OrderReconcileService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public void reconcilePendingOrders() {

        List<Order> pendingOrders =
                orderRepository.findTop100ByStatusAndCreatedAtAfterOrderByCreatedAtAsc(OrderStatus.PENDING);

        for (Order order : pendingOrders) {
            try {
                checkAndFixOrder(order);
            } catch (Exception e) {
                log.error("fail to reconcile order:{} ", order.getOrderNo());

            }
        }
    }

    private void checkAndFixOrder(Order order) throws Exception {

        String sessionId = order.getStripeSessionId();

        if (sessionId == null) {
            return;
        }

        // ⭐ 去 Stripe 查真实状态
        Session session = Session.retrieve(sessionId);

        if ("paid".equals(session.getPaymentStatus())) {

            // idempotence handle
            if (!"SUCCESS".equals(order.getStatus())) {

                order.setStatus(OrderStatus.PAID);
                orderRepository.save(order);

                System.out.println("补单成功: " + order.getOrderNo());
            }
        }
    }
}
