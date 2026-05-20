package realworld_backend.commerce.model.log;

/**
 * Business-domain classification for abnormal records.
 * Used to split reconcile pipelines for order/subscription/invoice/system.
 */
public enum AbnormalDomainType {
    ORDER,
    SUBSCRIPTION,
    INVOICE,
    SYSTEM
}

