package realworld_backend.commerce.service.entitlement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.service.metrics.CommerceMetricsService;
import realworld_backend.commerce.service.entitlement.policy.SubscriptionEntitlementPolicy;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EntitlementProjector {
    private final SubscriptionEntitlementPolicy subscriptionEntitlementPolicy;
    private final EntitlementCommandService entitlementCommandService;
    private final CommerceMetricsService commerceMetricsService;

    public void refreshSubscriptionEntitlement(CustomerSubscription subscription, LocalDateTime now) {
        subscriptionEntitlementPolicy.projectSubscription(subscription, now)
                .ifPresent(projection -> {
                    entitlementCommandService.upsertProjection(projection);
                    commerceMetricsService.recordEntitlementProjection(
                            projection.resourceType(),
                            projection.status()
                    );
                });
    }
}
