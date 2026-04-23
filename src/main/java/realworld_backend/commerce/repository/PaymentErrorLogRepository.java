package realworld_backend.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import realworld_backend.commerce.model.Order;
import realworld_backend.commerce.model.PaymentErrorLog;

public interface PaymentErrorLogRepository extends JpaRepository<PaymentErrorLog, Long> {
}

