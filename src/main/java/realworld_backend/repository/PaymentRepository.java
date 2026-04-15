package realworld_backend.repository;

import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import realworld_backend.model.Payment;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    @Modifying
    @Query("""
            UPDATE Payment p
            SET p.status = PaymentStatus.PROCESSING
            WHERE p.orderNo = :orderNo
            AND p.status = PaymentStatus.INIT
            """)
    int lockPayment(@Param("orderNo") String orderNo);

    @Override
    Optional<Payment> findById(@Param("id")Integer integer);

    Optional<Payment> findByOrderNo(@NotNull String orderNo);


}
