package realworld_backend.commerce.service.entitlement.policy;

import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.service.entitlement.EntitlementProjection;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EntitlementPolicy {
    Optional<EntitlementProjection> projectSubscription(CustomerSubscription subscription, LocalDateTime now);
}
