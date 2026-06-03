package realworld_backend.commerce.service.subscription.snapshot;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SubscriptionSnapshotPatch(
        String providerSubscriptionId,
        String providerCustomerId,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        Boolean cancelAtPeriodEnd,
        LocalDateTime canceledAt
) {
}
