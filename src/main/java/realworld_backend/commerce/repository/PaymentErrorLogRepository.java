package realworld_backend.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.commerce.model.Order;
import realworld_backend.commerce.model.PaymentErrorLog;
@Repository
public interface PaymentErrorLogRepository extends JpaRepository<PaymentErrorLog, Long> {
}

