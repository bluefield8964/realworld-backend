package realworld_backend.repository.CommerceRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import realworld_backend.model.commerceModule.Order;
import realworld_backend.model.commerceModule.PaymentErrorLog;

public interface PaymentErrorLogRepository extends JpaRepository<PaymentErrorLog, Long> {
}
