package realworld_backend.commerce.service.subscription.snapshot;

import org.springframework.stereotype.Service;
import realworld_backend.commerce.model.subscription.CustomerSubscription;

import java.time.LocalDateTime;

@Service
public class SubscriptionSnapshotMergeService {

    public boolean apply(
            CustomerSubscription local,
            SubscriptionSnapshotPatch patch,
            SubscriptionSnapshotMergePolicy policy
    ) {
        if (local == null || patch == null || policy == null) {
            return false;
        }

        boolean changed = false;

        changed |= mergeString(
                local.getProviderSubscriptionId(),
                patch.providerSubscriptionId(),
                policy.identityMode(),
                local::setProviderSubscriptionId
        );
        changed |= mergeString(
                local.getProviderCustomerId(),
                patch.providerCustomerId(),
                policy.identityMode(),
                local::setProviderCustomerId
        );
        changed |= mergeDateTime(
                local.getCurrentPeriodStart(),
                patch.currentPeriodStart(),
                policy.currentPeriodStartMode(),
                local::setCurrentPeriodStart
        );
        changed |= mergeDateTime(
                local.getCurrentPeriodEnd(),
                patch.currentPeriodEnd(),
                policy.currentPeriodEndMode(),
                local::setCurrentPeriodEnd
        );
        changed |= mergeBoolean(
                local.getCancelAtPeriodEnd(),
                patch.cancelAtPeriodEnd(),
                policy.cancelAtPeriodEndMode(),
                local::setCancelAtPeriodEnd
        );
        changed |= mergeDateTime(
                local.getCanceledAt(),
                patch.canceledAt(),
                policy.canceledAtMode(),
                local::setCanceledAt
        );

        return changed;
    }

    private boolean mergeString(
            String currentValue,
            String incomingValue,
            SubscriptionSnapshotWriteMode mode,
            java.util.function.Consumer<String> setter
    ) {
        if (mode == SubscriptionSnapshotWriteMode.NEVER) {
            return false;
        }
        if (!hasText(incomingValue)) {
            return false;
        }
        if (!hasText(currentValue)) {
            setter.accept(incomingValue);
            return true;
        }
        if (mode == SubscriptionSnapshotWriteMode.OVERWRITE && !incomingValue.equals(currentValue)) {
            setter.accept(incomingValue);
            return true;
        }
        return false;
    }

    private boolean mergeDateTime(
            LocalDateTime currentValue,
            LocalDateTime incomingValue,
            SubscriptionSnapshotWriteMode mode,
            java.util.function.Consumer<LocalDateTime> setter
    ) {
        if (mode == SubscriptionSnapshotWriteMode.NEVER) {
            return false;
        }
        if (incomingValue == null) {
            return false;
        }
        if (currentValue == null) {
            setter.accept(incomingValue);
            return true;
        }
        if (mode == SubscriptionSnapshotWriteMode.OVERWRITE && !incomingValue.equals(currentValue)) {
            setter.accept(incomingValue);
            return true;
        }
        if (mode == SubscriptionSnapshotWriteMode.ADVANCE_ONLY && incomingValue.isAfter(currentValue)) {
            setter.accept(incomingValue);
            return true;
        }
        return false;
    }

    private boolean mergeBoolean(
            Boolean currentValue,
            Boolean incomingValue,
            SubscriptionSnapshotWriteMode mode,
            java.util.function.Consumer<Boolean> setter
    ) {
        if (mode == SubscriptionSnapshotWriteMode.NEVER) {
            return false;
        }
        if (incomingValue == null) {
            return false;
        }
        if (currentValue == null) {
            setter.accept(incomingValue);
            return true;
        }
        if (mode == SubscriptionSnapshotWriteMode.OVERWRITE && !incomingValue.equals(currentValue)) {
            setter.accept(incomingValue);
            return true;
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
