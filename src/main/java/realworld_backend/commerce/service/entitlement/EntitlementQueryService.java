package realworld_backend.commerce.service.entitlement;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.model.entitlement.EntitlementGrant;
import realworld_backend.commerce.model.entitlement.enums.EntitlementResourceType;
import realworld_backend.commerce.model.entitlement.enums.EntitlementStatus;
import realworld_backend.commerce.repository.EntitlementGrantRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntitlementQueryService {
    private final EntitlementGrantRepository entitlementGrantRepository;

    public boolean hasResourceAccess(
            Long userId,
            EntitlementResourceType resourceType,
            String resourceId,
            LocalDateTime now
    ) {
        if (userId == null || resourceType == null || resourceId == null || resourceId.isBlank()) {
            return false;
        }
        LocalDateTime effectiveNow = now == null ? UtcTimeMapper.nowUtc() : now;
        if (entitlementGrantRepository.existsActiveResourceGrantWithoutExpireAt(
                userId,
                resourceType,
                resourceId,
                EntitlementStatus.ACTIVE,
                effectiveNow
        )) {
            return true;
        }
        return entitlementGrantRepository.existsActiveResourceGrantWithExpireAt(
                userId,
                resourceType,
                resourceId,
                EntitlementStatus.ACTIVE,
                effectiveNow,
                effectiveNow
        );
    }

    public boolean hasBundleAccess(Long userId, String bundleCode, LocalDateTime now) {
        return hasResourceAccess(userId, EntitlementResourceType.BUNDLE, bundleCode, now);
    }

    public boolean hasContentAccess(Long userId, String contentId, LocalDateTime now) {
        return hasResourceAccess(userId, EntitlementResourceType.CONTENT, contentId, now);
    }

    public boolean hasAnyBundleAccess(Long userId, LocalDateTime now) {
        if (userId == null) {
            return false;
        }
        LocalDateTime effectiveNow = now == null ? UtcTimeMapper.nowUtc() : now;
        List<EntitlementGrant> entitlementGrant 
                = entitlementGrantRepository.findByUserIdAndStatusOrderByExpireAtAsc(userId, EntitlementStatus.ACTIVE);
        boolean allowed = entitlementGrant.stream().filter(grant -> grant.getResourceType() == EntitlementResourceType.BUNDLE)
                .anyMatch(grant -> grant.getEffectiveAt() != null
                        && grant.getEffectiveAt().isBefore(effectiveNow)
                        && (grant.getExpireAt() == null || grant.getExpireAt().isAfter(effectiveNow)));

        log.info("hasAnyBundleAccess userId={} allowed={}", userId, allowed);
        return allowed;
    }

    public List<EntitlementGrant> listActiveEntitlements(Long userId) {
        if (userId == null) {
            return List.of();
        }
        LocalDateTime now = UtcTimeMapper.nowUtc();
        List<EntitlementGrant> grants = entitlementGrantRepository.findActiveEntitlements(
                userId,
                EntitlementStatus.ACTIVE,
                now,
                now
        );
        log.info("listActiveEntitlements userId={} size={}", userId, grants.size());
        return grants;
    }
}

