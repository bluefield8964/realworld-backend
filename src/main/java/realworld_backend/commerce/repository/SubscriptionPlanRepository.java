package realworld_backend.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.commerce.model.subscription.SubscriptionPlan;
import realworld_backend.commerce.model.subscription.enums.PlanStatus;

import java.util.Optional;
import java.util.List;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    Optional<SubscriptionPlan> findByplanCode(String planCode);
    List<SubscriptionPlan> findAllByStatus(PlanStatus status);

}

