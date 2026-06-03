package realworld_backend.commerce.service.subscription;

import lombok.RequiredArgsConstructor;
import realworld_backend.commerce.service.subscription.invoice.SubscriptionInvoiceWebhookService;
import realworld_backend.commerce.service.subscription.lifecycle.SubscriptionLifecycleWebhookService;
import realworld_backend.commerce.service.subscription.lifecycle.SubscriptionSnapshotSyncService;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import org.springframework.stereotype.Service;

/**
 * Thin facade that dispatches subscription webhooks by ownership:
 * lifecycle events, invoice billing facts, and pure snapshot-only events.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionWebhookService {
    private final SubscriptionLifecycleWebhookService subscriptionLifecycleWebhookService;
    private final SubscriptionInvoiceWebhookService subscriptionInvoiceWebhookService;
    private final SubscriptionSnapshotSyncService subscriptionSnapshotSyncService;

    public void handleSubscriptionDeletedEvent(WebhookContext ctx) {
        subscriptionLifecycleWebhookService.handleSubscriptionDeletedEvent(ctx);
    }

    public void handleSubscriptionCreatedEvent(WebhookContext ctx) {
        subscriptionLifecycleWebhookService.handleSubscriptionCreatedEvent(ctx);
    }

    public void handleSubscriptionPausedEvent(WebhookContext ctx) {
        subscriptionLifecycleWebhookService.handleSubscriptionPausedEvent(ctx);
    }

    public void handleSubscriptionResumedEvent(WebhookContext ctx) {
        subscriptionLifecycleWebhookService.handleSubscriptionResumedEvent(ctx);
    }

    public void handleSubscriptionLifecycleUpdatedEvent(WebhookContext ctx) {
        subscriptionLifecycleWebhookService.handleSubscriptionLifecycleUpdatedEvent(ctx);
    }

    public void handleSubscriptionTrialWillEndEvent(WebhookContext ctx) {
        subscriptionSnapshotSyncService.handleSubscriptionTrialWillEndEvent(ctx);
    }

    public void handleInvoicePaymentFailed(WebhookContext ctx) {
        subscriptionInvoiceWebhookService.handleInvoicePaymentFailed(ctx);
    }

    public void handleInvoicePaymentSucceeded(WebhookContext ctx) {
        subscriptionInvoiceWebhookService.handleInvoicePaymentSucceeded(ctx);
    }

    public void handleInvoicePaymentActionRequired(WebhookContext ctx) {
        subscriptionInvoiceWebhookService.handleInvoicePaymentActionRequired(ctx);
    }
}
