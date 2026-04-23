package realworld_backend.commerce.repository;

import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import realworld_backend.commerce.model.Payment;
import realworld_backend.commerce.model.PaymentStatus;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Modifying
    @Query("""
            UPDATE Payment p
            SET p.status = PaymentStatus.PROCESSING
            WHERE p.orderNo = :orderNo
            AND p.status = PaymentStatus.INIT
            """)
    int lockPayment(@Param("orderNo") String orderNo);

    @Override
    Optional<Payment> findById(@Param("id")Long integer);

    Optional<Payment> findByOrderNo(@NotNull String orderNo);


    Optional<Payment>  findBySessionId(String sessionId);


    @Modifying
    @Query("""
UPDATE Payment p
SET p.status = PaymentStatus.SUCCESS
WHERE p.sessionId = :sessionId
AND p.status <> PaymentStatus.SUCCESS
""")
    int markPaidIfNotPaid(@Param("sessionId")String sessionId);
}

