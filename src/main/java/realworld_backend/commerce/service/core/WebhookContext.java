package realworld_backend.commerce.service.core;

import lombok.Builder;
import lombok.Data;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.core.ProviderEventInterface;
import realworld_backend.commerce.model.core.ProviderRawEvent;

@Data
@Builder
public class WebhookContext {
    private String provider;
    private String eventId;
    private BusinessEventType eventType;
    // Business tracking key of current event:
    // - order: orderNo
    // - subscription: business subscriptionNo
    // - invoice: business subscriptionNo/orderNo
    private String trackingId;
    // Provider object id used for retrieve/reconcile:
    // - order: checkout session id
    // - subscription: provider subscription id when needed
    // - invoice: provider invoice id
    private String providerTrackingId;
    private ProviderEventInterface providerEvent;
    private ProviderRawEvent providerRawEvent;
    private int attempts;
    private boolean abnormalAlreadyUpserted;


}

