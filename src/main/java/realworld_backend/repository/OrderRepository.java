package realworld_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import realworld_backend.model.Order;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    Optional<Order> findByOrderNo(String orderNo);

    Optional<Order> findByStripeSessionId(String sessionId);

    Optional<Order> findByUserIdAndStatus(Long userId, String status);

    @Modifying
    @Query("""
UPDATE Order o
SET o.status = 'PENDING'
WHERE o.orderNo = :orderNo
AND o.status = 'CREATED'
""")
    int lockOrder(@Param("orderNo") String orderNo);


}
