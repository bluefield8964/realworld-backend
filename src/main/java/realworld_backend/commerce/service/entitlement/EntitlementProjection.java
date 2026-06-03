package realworld_backend.commerce.service.entitlement;

import realworld_backend.commerce.model.entitlement.enums.EntitlementResourceType;
import realworld_backend.commerce.model.entitlement.enums.EntitlementSourceType;
import realworld_backend.commerce.model.entitlement.enums.EntitlementStatus;

import java.time.LocalDateTime;

public record EntitlementProjection(
        Long userId,
        EntitlementResourceType resourceType,
        String resourceId,
        EntitlementSourceType sourceType,
        String sourceId,
        EntitlementStatus status,
        LocalDateTime effectiveAt,
        LocalDateTime expireAt
) {
}
