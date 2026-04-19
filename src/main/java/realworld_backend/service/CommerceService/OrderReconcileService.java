package realworld_backend.service.CommerceService;

import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import realworld_backend.model.commerceModule.Order;
import realworld_backend.model.commerceModule.OrderStatus;
import realworld_backend.repository.OrderRepository;

import java.util.List;

@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class OrderReconcileService {
    private final OrderRepository orderRepository;

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
            if (!OrderStatus.PAID.equals(order.getStatus())) {

                order.setStatus(OrderStatus.PAID);
                orderRepository.save(order);

                log.error("success to reconcile order:{} ", order.getOrderNo());
            }
        }
    }
}
