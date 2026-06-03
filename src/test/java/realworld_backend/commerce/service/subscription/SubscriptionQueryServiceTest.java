package realworld_backend.commerce.service.subscription;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionQueryServiceTest {

    @Mock
    private CustomerSubscriptionService customerSubscriptionService;

    @Mock
    private SubscriptionAccessService subscriptionAccessService;

    @InjectMocks
    private SubscriptionQueryService subscriptionQueryService;

    @Test
    void getCurrentUserSubscriptionShouldReturnEmptyViewWhenUserHasNoSubscription() {
        CurrentAuthUser currentUser = new CurrentAuthUser(11L, "session_11");
        when(customerSubscriptionService.findByUserIdOrderByCurrentPeriodEndDesc(11L))
                .thenReturn(List.of());

        SubscriptionQueryService.SubscriptionMeView view =
                subscriptionQueryService.getCurrentUserSubscription(currentUser);

        assertFalse(view.hasSubscription());
        assertFalse(view.accessGranted());
        assertNull(view.status());
    }

    @Test
    void getCurrentUserSubscriptionShouldPreferEffectiveActiveSubscription() {
        CurrentAuthUser currentUser = new CurrentAuthUser(12L, "session_12");
        CustomerSubscription pastDue = CustomerSubscription.builder()
                .subscriptionNo("sub_past_due")
                .status(SubscriptionStatus.PAST_DUE)
                .currentPeriodEnd(LocalDateTime.now().plusDays(1))
                .cancelAtPeriodEnd(false)
                .build();
        CustomerSubscription active = CustomerSubscription.builder()
                .subscriptionNo("sub_active")
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(LocalDateTime.now().plusDays(5))
                .cancelAtPeriodEnd(true)
                .build();

        when(customerSubscriptionService.findByUserIdOrderByCurrentPeriodEndDesc(12L))
                .thenReturn(List.of(pastDue, active));
        when(subscriptionAccessService.hasActiveSubscription(eq(12L), any()))
                .thenReturn(true);

        SubscriptionQueryService.SubscriptionMeView view =
                subscriptionQueryService.getCurrentUserSubscription(currentUser);

        assertTrue(view.hasSubscription());
        assertTrue(view.accessGranted());
        assertEquals("sub_active", view.subscriptionNo());
        assertEquals(SubscriptionStatus.ACTIVE, view.status());
        assertTrue(Boolean.TRUE.equals(view.cancelAtPeriodEnd()));
    }
}
