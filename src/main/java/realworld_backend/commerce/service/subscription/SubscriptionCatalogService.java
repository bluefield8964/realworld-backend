package realworld_backend.commerce.service.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.model.subscription.PlanProviderMapping;
import realworld_backend.commerce.model.subscription.SubscriptionPlan;
import realworld_backend.commerce.model.subscription.enums.PlanStatus;
import realworld_backend.commerce.model.subscription.enums.ProviderType;
import realworld_backend.commerce.repository.SubscriptionPlanRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SubscriptionCatalogService {
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PlanProviderMappingService planProviderMappingService;

    public List<SubscriptionPlanOption> getAvailableOptions(ProviderType provider) {
        return subscriptionPlanRepository.findAllByStatus(PlanStatus.ACTIVE).stream()
                .map(plan -> toOption(plan, provider))
                .filter(Objects::nonNull)
                .toList();
    }

    private SubscriptionPlanOption toOption(SubscriptionPlan plan, ProviderType provider) {
        PlanProviderMapping mapping = planProviderMappingService.findMappingByPlanAndProvider(plan, provider);
        if (mapping == null || mapping.getActive() == null || !mapping.getActive()) {
            return null;
        }
        return new SubscriptionPlanOption(
                plan.getPlanCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getCurrency(),
                plan.getBillingInterval().name(),
                plan.getDuration(),
                provider.name(),
                mapping.getProviderPriceId(),
                mapping.getProviderProductId()
        );
    }

    public record SubscriptionPlanOption(
            String planCode,
            String name,
            String description,
            BigDecimal price,
            String currency,
            String billingInterval,
            Duration duration,
            String provider,
            String providerPriceId,
            String providerProductId
    ) {
    }
}
