package realworld_backend.commerce.service.entitlement.policy;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.model.entitlement.enums.EntitlementResourceType;
import realworld_backend.commerce.model.entitlement.enums.EntitlementSourceType;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.FeatureBundle;
import realworld_backend.commerce.service.entitlement.EntitlementProjection;
import realworld_backend.commerce.service.entitlement.EntitlementStatusDecision;
import realworld_backend.commerce.service.entitlement.EntitlementStatusMachine;

import java.time.LocalDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class SubscriptionEntitlementPolicy implements EntitlementPolicy {
    private final EntitlementStatusMachine entitlementStatusMachine;

    @Override
    public Optional<EntitlementProjection> projectSubscription(CustomerSubscription subscription, LocalDateTime now) {
        if (subscription == null
                || subscription.getUser() == null
                || subscription.getPlan() == null
                || subscription.getSubscriptionNo() == null) {
            return Optional.empty();
        }

        LocalDateTime effectiveNow = now == null ? UtcTimeMapper.nowUtc() : now;
        EntitlementStatusDecision decision =
                entitlementStatusMachine.evaluateSubscription(subscription, effectiveNow);
        FeatureBundle featureBundle = subscription.getPlan().getFeatureBundle();
        if (featureBundle == null || featureBundle.getBundleCode() == null || featureBundle.getBundleCode().isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new EntitlementProjection(
                subscription.getUser().getId(),
                EntitlementResourceType.BUNDLE,
                featureBundle.getBundleCode(),
                EntitlementSourceType.SUBSCRIPTION,
                subscription.getSubscriptionNo(),
                decision.status(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd()
        ));
    }
}

