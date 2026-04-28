package realworld_backend.commerce.model.log;

public enum AbnormalOrderStatus {
    PENDING,            // Waiting for abnormal reconcile job
    RECONCILING,        // Reconciliation is in progress
    FIXED,              // Reconciled and closed
    UNPAID_CONFIRMED,   // Confirmed unpaid on provider side
    EXHAUSTED,          // Reconcile retry budget exhausted
    MANUAL_REVIEW,      // Needs manual review

    // Legacy values kept for existing rows before abnormalType/status split.
    ORDER_MISSING,
    PAYMENT_MISSING,
    SUBSCRIPTION_MISSING,
    SUBSCRIPTION_HISTORY_CHANGE_FAIL,
    RETRY_EXHAUSTED
}


