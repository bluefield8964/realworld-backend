package realworld_backend.repository.CommerceRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.model.commerceModule.Payment;

@Repository
public interface ProductRepository extends JpaRepository<Payment, Long> {



}
