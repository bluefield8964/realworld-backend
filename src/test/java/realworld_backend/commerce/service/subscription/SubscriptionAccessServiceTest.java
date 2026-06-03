package realworld_backend.commerce.service.subscription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.commerce.service.entitlement.EntitlementFacade;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionAccessServiceTest {

    @Mock
    private EntitlementFacade entitlementFacade;

    @InjectMocks
    private SubscriptionAccessService subscriptionAccessService;

    @Test
    void requireActiveSubscriptionShouldPassWhenUserHasEffectiveActiveSubscription() {
        CurrentAuthUser currentUser = new CurrentAuthUser(1L, "session_1");
        when(entitlementFacade.hasAnyBundleAccess(eq(1L), any())).thenReturn(true);

        assertDoesNotThrow(() -> subscriptionAccessService.requireActiveSubscription(currentUser));
    }

    @Test
    void requireActiveSubscriptionShouldFailWhenUserHasNoEffectiveEntitlement() {
        CurrentAuthUser currentUser = new CurrentAuthUser(2L, "session_2");
        when(entitlementFacade.hasAnyBundleAccess(eq(2L), any())).thenReturn(false);

        BizException exception = assertThrows(
                BizException.class,
                () -> subscriptionAccessService.requireActiveSubscription(currentUser)
        );

        assertEquals(ErrorCode.SUBSCRIPTION_REQUIRED, exception.getErrorCode());
    }

    @Test
    void hasActiveSubscriptionShouldRejectNonAccessStatuses() {
        when(entitlementFacade.hasAnyBundleAccess(eq(3L), any())).thenReturn(false);

        assertFalse(subscriptionAccessService.hasActiveSubscription(3L, LocalDateTime.now()));
    }
}
