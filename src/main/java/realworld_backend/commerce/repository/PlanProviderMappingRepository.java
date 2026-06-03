package realworld_backend.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.commerce.model.subscription.PlanProviderMapping;
import realworld_backend.commerce.model.subscription.enums.ProviderType;
@Repository
public interface PlanProviderMappingRepository extends JpaRepository<PlanProviderMapping, Long> {
    PlanProviderMapping findByPlanIdAndProvider(Long id, ProviderType provider);
}

