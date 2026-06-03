package realworld_backend.commerce.service.entitlement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import realworld_backend.commerce.model.entitlement.enums.EntitlementResourceType;
import realworld_backend.commerce.model.entitlement.enums.EntitlementStatus;
import realworld_backend.commerce.repository.EntitlementGrantRepository;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementQueryServiceTest {

    @Mock
    private EntitlementGrantRepository entitlementGrantRepository;

    @InjectMocks
    private EntitlementQueryService entitlementQueryService;

    @Test
    void hasBundleAccessShouldDelegateToBundleResourceLookup() {
        when(entitlementGrantRepository.existsByUserIdAndResourceTypeAndResourceIdAndStatusAndEffectiveAtLessThanEqualAndExpireAtIsNull(
                eq(1L),
                eq(EntitlementResourceType.BUNDLE),
                eq("CREATOR_PRO_BUNDLE"),
                eq(EntitlementStatus.ACTIVE),
                any()
        )).thenReturn(true);

        boolean allowed = entitlementQueryService.hasBundleAccess(1L, "CREATOR_PRO_BUNDLE", LocalDateTime.now());

        assertTrue(allowed);
        verify(entitlementGrantRepository).existsByUserIdAndResourceTypeAndResourceIdAndStatusAndEffectiveAtLessThanEqualAndExpireAtIsNull(
                eq(1L),
                eq(EntitlementResourceType.BUNDLE),
                eq("CREATOR_PRO_BUNDLE"),
                eq(EntitlementStatus.ACTIVE),
                any()
        );
    }

    @Test
    void hasContentAccessShouldDelegateToContentResourceLookup() {
        when(entitlementGrantRepository.existsByUserIdAndResourceTypeAndResourceIdAndStatusAndEffectiveAtLessThanEqualAndExpireAtIsNull(
                eq(2L),
                eq(EntitlementResourceType.CONTENT),
                eq("creator_1001"),
                eq(EntitlementStatus.ACTIVE),
                any()
        )).thenReturn(true);

        boolean allowed = entitlementQueryService.hasContentAccess(2L, "creator_1001", LocalDateTime.now());

        assertTrue(allowed);
        verify(entitlementGrantRepository).existsByUserIdAndResourceTypeAndResourceIdAndStatusAndEffectiveAtLessThanEqualAndExpireAtIsNull(
                eq(2L),
                eq(EntitlementResourceType.CONTENT),
                eq("creator_1001"),
                eq(EntitlementStatus.ACTIVE),
                any()
        );
    }

    @Test
    void hasAnyBundleAccessShouldCheckBundleEntitlementsWithoutResourceId() {
        when(entitlementGrantRepository.existsByUserIdAndResourceTypeAndStatusAndEffectiveAtLessThanEqualAndExpireAtIsNull(
                eq(3L),
                eq(EntitlementResourceType.BUNDLE),
                eq(EntitlementStatus.ACTIVE),
                any()
        )).thenReturn(true);

        boolean allowed = entitlementQueryService.hasAnyBundleAccess(3L, LocalDateTime.now());

        assertTrue(allowed);
        verify(entitlementGrantRepository).existsByUserIdAndResourceTypeAndStatusAndEffectiveAtLessThanEqualAndExpireAtIsNull(
                eq(3L),
                eq(EntitlementResourceType.BUNDLE),
                eq(EntitlementStatus.ACTIVE),
                any()
        );
    }
}
