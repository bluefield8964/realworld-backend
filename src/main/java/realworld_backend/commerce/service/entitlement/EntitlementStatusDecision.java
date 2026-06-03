package realworld_backend.commerce.service.entitlement;

import realworld_backend.commerce.model.entitlement.enums.EntitlementStatus;

public record EntitlementStatusDecision(
        EntitlementStatus status,
        boolean alive,
        boolean terminal,
        String reason
) {
}
