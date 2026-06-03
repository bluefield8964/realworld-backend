package realworld_backend.commerce.service.subscription;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionQueryService {
    private final CustomerSubscriptionService customerSubscriptionService;
    private final SubscriptionAccessService subscriptionAccessService;

    public SubscriptionMeView getCurrentUserSubscription(CurrentAuthUser currentUser) {
        if (currentUser == null || currentUser.userId() == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        List<CustomerSubscription> subscriptions =
                customerSubscriptionService.findByUserIdOrderByCurrentPeriodEndDesc(currentUser.userId());

        CustomerSubscription selected = pickCurrentSubscription(subscriptions, UtcTimeMapper.nowUtc());
        if (selected == null) {
            return new SubscriptionMeView(false, false, null, null, null, null);
        }

        boolean accessGranted =
                subscriptionAccessService.hasActiveSubscription(currentUser.userId(), UtcTimeMapper.nowUtc());

        return new SubscriptionMeView(
                true,
                accessGranted,
                selected.getSubscriptionNo(),
                selected.getStatus(),
                selected.getCurrentPeriodEnd(),
                selected.getCancelAtPeriodEnd()
        );
    }

    private CustomerSubscription pickCurrentSubscription(
            List<CustomerSubscription> subscriptions,
            LocalDateTime now
    ) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            return null;
        }

        return subscriptions.stream()
                .sorted(currentSubscriptionComparator(now))
                .findFirst()
                .orElse(null);
    }

    private Comparator<CustomerSubscription> currentSubscriptionComparator(LocalDateTime now) {
        return Comparator
                .comparing((CustomerSubscription subscription) -> subscriptionPriority(subscription, now))
                .thenComparing(
                        CustomerSubscription::getCurrentPeriodEnd,
                        Comparator.nullsLast(Comparator.reverseOrder())
                );
    }

    private int subscriptionPriority(CustomerSubscription subscription, LocalDateTime now) {
        if (subscription == null || subscription.getStatus() == null) {
            return 99;
        }

        LocalDateTime currentPeriodEnd = subscription.getCurrentPeriodEnd();
        boolean notExpired = currentPeriodEnd == null || now.isBefore(currentPeriodEnd);

        if ((subscription.getStatus() == SubscriptionStatus.ACTIVE
                || subscription.getStatus() == SubscriptionStatus.TRIALING)
                && notExpired) {
            return 0;
        }
        if (subscription.getStatus() == SubscriptionStatus.PAST_DUE) {
            return 1;
        }
        if (subscription.getStatus() == SubscriptionStatus.PAUSED) {
            return 2;
        }
        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            return 3;
        }
        return 4;
    }

    public record SubscriptionMeView(
            boolean hasSubscription,
            boolean accessGranted,
            String subscriptionNo,
            SubscriptionStatus status,
            LocalDateTime currentPeriodEnd,
            Boolean cancelAtPeriodEnd
    ) {
    }
}

