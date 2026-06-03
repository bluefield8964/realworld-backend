package realworld_backend.commerce.service.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.entitlement.enums.EntitlementResourceType;
import realworld_backend.commerce.model.entitlement.enums.EntitlementStatus;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;

@Service
@RequiredArgsConstructor
public class CommerceMetricsService {
    private final MeterRegistry meterRegistry;

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordWebhookReceived(String provider, String family, String event) {
        meterRegistry.counter(
                "commerce_webhook_received_total",
                "provider", safe(provider),
                "family", safe(family),
                "event", safe(event)
        ).increment();
    }

    public void recordWebhookResult(String provider, String family, String result) {
        meterRegistry.counter(
                "commerce_webhook_result_total",
                "provider", safe(provider),
                "family", safe(family),
                "result", safe(result)
        ).increment();
    }

    public void recordWebhookDuration(Timer.Sample sample, String provider, String family, String result) {
        if (sample == null) {
            return;
        }
        sample.stop(
                Timer.builder("commerce_webhook_duration_seconds")
                        .tag("provider", safe(provider))
                        .tag("family", safe(family))
                        .tag("result", safe(result))
                        .register(meterRegistry)
        );
    }

    public void recordSubscriptionTransition(String source, SubscriptionStatus from, SubscriptionStatus to) {
        meterRegistry.counter(
                "commerce_subscription_transition_total",
                "source", safe(source),
                "from", safeEnum(from),
                "to", safeEnum(to)
        ).increment();
    }

    public void recordEntitlementProjection(EntitlementResourceType resourceType, EntitlementStatus status) {
        meterRegistry.counter(
                "commerce_entitlement_projection_total",
                "resource_type", safeEnum(resourceType),
                "status", safeEnum(status)
        ).increment();
    }

    public void recordReconcileScanned(SubscriptionStatus status) {
        meterRegistry.counter(
                "commerce_reconcile_scanned_total",
                "status", safeEnum(status)
        ).increment();
    }

    public void recordReconcileFixed(SubscriptionStatus from, SubscriptionStatus to) {
        meterRegistry.counter(
                "commerce_reconcile_fixed_total",
                "from", safeEnum(from),
                "to", safeEnum(to)
        ).increment();
    }

    public void recordReconcileIncident(SubscriptionStatus status) {
        meterRegistry.counter(
                "commerce_reconcile_incident_total",
                "status", safeEnum(status)
        ).increment();
    }

    public void recordReconcileDuration(Timer.Sample sample, String phase) {
        if (sample == null) {
            return;
        }
        sample.stop(
                Timer.builder("commerce_reconcile_duration_seconds")
                        .tag("phase", safe(phase))
                        .register(meterRegistry)
        );
    }

    public String familyOf(BusinessEventType eventType) {
        if (eventType == null) {
            return "unknown";
        }
        String name = eventType.name();
        if (name.startsWith("CHECKOUT_SESSION_")) {
            return "checkout";
        }
        if (name.startsWith("SUBSCRIPTION_") || name.startsWith("CUSTOMER_SUBSCRIPTION_")) {
            return "subscription";
        }
        if (name.startsWith("INVOICE_")) {
            return "invoice";
        }
        if (name.startsWith("ORDER_")) {
            return "order";
        }
        if (name.startsWith("REFUND_") || name.startsWith("CHARGE_")) {
            return "refund";
        }
        return "other";
    }

    public String eventOf(BusinessEventType eventType) {
        return eventType == null ? "unknown" : eventType.name().toLowerCase();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String safeEnum(Enum<?> value) {
        return value == null ? "unknown" : value.name().toLowerCase();
    }
}
