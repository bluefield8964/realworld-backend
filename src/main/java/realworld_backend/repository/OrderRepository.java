package realworld_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.model.Order;
import realworld_backend.model.OrderStatus;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    Optional<Order> findByOrderNo(String orderNo);

    List<Order> findTop100ByStatusAndCreatedAtAfterOrderByCreatedAtAsc(OrderStatus status);
    Optional<Order> findByStripeSessionId(String sessionId);

    @Query("""
SELECT o FROM Order o
WHERE o.userId = :userId
AND o.status IN :statusList
""")
    Optional<Order> findByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("statusList") List<OrderStatus> statusList
    );

    @Modifying
    @Transactional
    @Query("""
UPDATE Order o
SET o.status = OrderStatus.PENDING
WHERE o.orderNo = :orderNo
AND o.status = OrderStatus.CREATED
""")
    int lockOrder(@Param("orderNo") String orderNo);


    Optional<Order> findByActiveKey(String activeKey);
}
