package realworld_backend.commerce.service.subscription;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.model.subscription.PlanProviderMapping;
import realworld_backend.commerce.model.subscription.SubscriptionPlan;
import realworld_backend.commerce.model.subscription.enums.ProviderType;
import realworld_backend.commerce.repository.PlanProviderMappingRepository;

@Service
@AllArgsConstructor
public class PlanProviderMappingService {
    private final PlanProviderMappingRepository planProviderMappingRepository;

    public  PlanProviderMapping findMappingByPlanAndProvider(SubscriptionPlan plan, ProviderType provider) {
        return planProviderMappingRepository.findByPlanIdAndProvider(plan.getId(),provider.name());

    }
}

