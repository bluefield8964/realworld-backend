package realworld_backend.commerce.job;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.service.subscription.SubscriptionStalledReconcileService;

@RequiredArgsConstructor
@Component
/**
 * Background reconcile for subscriptions stuck in pre-activation states too long.
 * Runs outside the request path so UX is not blocked by provider pull checks.
 */
public class SubscriptionStalledReconcileJob {

    private final SubscriptionStalledReconcileService subscriptionStalledReconcileService;

    @Scheduled(fixedDelay = 300000)
    public void run() {
        subscriptionStalledReconcileService.reconcileStalledSubscriptions();
    }
}
