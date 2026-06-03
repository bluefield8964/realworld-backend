package realworld_backend.commerce.service.subscription.snapshot;

/**
 * Field-level merge policy for provider-derived subscription snapshot data.
 * Status transitions stay in the state machine; this policy only governs
 * non-status snapshot fields.
 */
public record SubscriptionSnapshotMergePolicy(
        SubscriptionSnapshotWriteMode identityMode,
        SubscriptionSnapshotWriteMode currentPeriodStartMode,
        SubscriptionSnapshotWriteMode currentPeriodEndMode,
        SubscriptionSnapshotWriteMode cancelAtPeriodEndMode,
        SubscriptionSnapshotWriteMode canceledAtMode
) {
    public static SubscriptionSnapshotMergePolicy checkoutHint() {
        return new SubscriptionSnapshotMergePolicy(
                SubscriptionSnapshotWriteMode.FILL_IF_MISSING,
                SubscriptionSnapshotWriteMode.FILL_IF_MISSING,
                SubscriptionSnapshotWriteMode.FILL_IF_MISSING,
                SubscriptionSnapshotWriteMode.NEVER,
                SubscriptionSnapshotWriteMode.NEVER
        );
    }

    public static SubscriptionSnapshotMergePolicy invoiceIdentityOnly() {
        return new SubscriptionSnapshotMergePolicy(
                SubscriptionSnapshotWriteMode.FILL_IF_MISSING,
                SubscriptionSnapshotWriteMode.NEVER,
                SubscriptionSnapshotWriteMode.NEVER,
                SubscriptionSnapshotWriteMode.NEVER,
                SubscriptionSnapshotWriteMode.NEVER
        );
    }

    public static SubscriptionSnapshotMergePolicy invoiceConservative() {
        return new SubscriptionSnapshotMergePolicy(
                SubscriptionSnapshotWriteMode.FILL_IF_MISSING,
                SubscriptionSnapshotWriteMode.ADVANCE_ONLY,
                SubscriptionSnapshotWriteMode.ADVANCE_ONLY,
                SubscriptionSnapshotWriteMode.NEVER,
                SubscriptionSnapshotWriteMode.NEVER
        );
    }

    public static SubscriptionSnapshotMergePolicy lifecycleAuthoritative() {
        return new SubscriptionSnapshotMergePolicy(
                SubscriptionSnapshotWriteMode.OVERWRITE,
                SubscriptionSnapshotWriteMode.OVERWRITE,
                SubscriptionSnapshotWriteMode.OVERWRITE,
                SubscriptionSnapshotWriteMode.OVERWRITE,
                SubscriptionSnapshotWriteMode.OVERWRITE
        );
    }

    public static SubscriptionSnapshotMergePolicy snapshotConservative() {
        return new SubscriptionSnapshotMergePolicy(
                SubscriptionSnapshotWriteMode.FILL_IF_MISSING,
                SubscriptionSnapshotWriteMode.ADVANCE_ONLY,
                SubscriptionSnapshotWriteMode.ADVANCE_ONLY,
                SubscriptionSnapshotWriteMode.OVERWRITE,
                SubscriptionSnapshotWriteMode.OVERWRITE
        );
    }
}
