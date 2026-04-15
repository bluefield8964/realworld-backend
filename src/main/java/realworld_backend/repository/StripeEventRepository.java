package realworld_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.model.StripeEvent;

@Repository
public interface StripeEventRepository extends JpaRepository<StripeEvent, String> {

}
