package realworld_backend.commerce.service.entitlement;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.commerce.model.entitlement.EntitlementGrant;
import realworld_backend.commerce.repository.EntitlementGrantRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EntitlementCommandService {
    private final EntitlementGrantRepository entitlementGrantRepository;

    @Transactional
    public EntitlementGrant upsertProjection(EntitlementProjection projection) {
        LocalDateTime now = UtcTimeMapper.nowUtc();
        EntitlementGrant grant = entitlementGrantRepository
                .findBySourceTypeAndSourceIdAndResourceTypeAndResourceId(
                        projection.sourceType(),
                        projection.sourceId(),
                        projection.resourceType(),
                        projection.resourceId()
                )
                .orElseGet(() -> EntitlementGrant.builder()
                        .sourceType(projection.sourceType())
                        .sourceId(projection.sourceId())
                        .resourceType(projection.resourceType())
                        .resourceId(projection.resourceId())
                        .version(0L)
                        .createdAt(now)
                .build());

        grant.setUserId(projection.userId());
        grant.setStatus(projection.status());
        grant.setEffectiveAt(projection.effectiveAt());
        grant.setExpireAt(projection.expireAt());
        grant.setUpdatedAt(now);
        grant.setVersion(grant.getVersion() == null ? 1L : grant.getVersion() + 1);
        return entitlementGrantRepository.save(grant);
    }
}

