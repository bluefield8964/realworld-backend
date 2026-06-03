package realworld_backend.commerce.service.entitlement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.model.entitlement.EntitlementGrant;
import realworld_backend.commerce.model.entitlement.enums.EntitlementResourceType;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EntitlementFacade {
    private final EntitlementQueryService entitlementQueryService;
    private final EntitlementProjector entitlementProjector;

    public boolean hasResourceAccess(
            Long userId,
            EntitlementResourceType resourceType,
            String resourceId,
            LocalDateTime now
    ) {
        return entitlementQueryService.hasResourceAccess(userId, resourceType, resourceId, now);
    }

    public boolean hasBundleAccess(Long userId, String bundleCode, LocalDateTime now) {
        return entitlementQueryService.hasBundleAccess(userId, bundleCode, now);
    }

    public boolean hasContentAccess(Long userId, String contentId, LocalDateTime now) {
        return entitlementQueryService.hasContentAccess(userId, contentId, now);
    }

    public boolean hasAnyBundleAccess(Long userId, LocalDateTime now) {
        return entitlementQueryService.hasAnyBundleAccess(userId, now);
    }

    public List<EntitlementGrant> listActiveEntitlements(Long userId) {
        return entitlementQueryService.listActiveEntitlements(userId);
    }

    public EntitlementProjector projector() {
        return entitlementProjector;
    }
}
