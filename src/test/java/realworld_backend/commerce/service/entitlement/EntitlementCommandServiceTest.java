package realworld_backend.commerce.service.entitlement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import realworld_backend.commerce.model.entitlement.EntitlementGrant;
import realworld_backend.commerce.model.entitlement.enums.EntitlementResourceType;
import realworld_backend.commerce.model.entitlement.enums.EntitlementSourceType;
import realworld_backend.commerce.model.entitlement.enums.EntitlementStatus;
import realworld_backend.commerce.repository.EntitlementGrantRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementCommandServiceTest {

    @Mock
    private EntitlementGrantRepository entitlementGrantRepository;

    @InjectMocks
    private EntitlementCommandService entitlementCommandService;

    @Test
    void upsertProjectionShouldPersistBundleGrant() {
        EntitlementProjection projection = new EntitlementProjection(
                1L,
                EntitlementResourceType.BUNDLE,
                "CREATOR_PRO_BUNDLE",
                EntitlementSourceType.SUBSCRIPTION,
                "sub_no_1",
                EntitlementStatus.ACTIVE,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(7)
        );

        when(entitlementGrantRepository.findBySourceTypeAndSourceIdAndResourceTypeAndResourceId(
                eq(EntitlementSourceType.SUBSCRIPTION),
                eq("sub_no_1"),
                eq(EntitlementResourceType.BUNDLE),
                eq("CREATOR_PRO_BUNDLE")
        )).thenReturn(Optional.empty());
        when(entitlementGrantRepository.save(any(EntitlementGrant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EntitlementGrant saved = entitlementCommandService.upsertProjection(projection);

        assertEquals(EntitlementResourceType.BUNDLE, saved.getResourceType());
        assertEquals("CREATOR_PRO_BUNDLE", saved.getResourceId());
        assertEquals(EntitlementStatus.ACTIVE, saved.getStatus());
        verify(entitlementGrantRepository).save(any(EntitlementGrant.class));
    }
}
