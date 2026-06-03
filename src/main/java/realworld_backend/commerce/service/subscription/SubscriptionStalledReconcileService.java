package realworld_backend.commerce.service.subscription;

import realworld_backend.common.time.UtcTimeMapper;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.core.ProviderSubscription;
import realworld_backend.commerce.model.log.AbnormalDomainType;
import realworld_backend.commerce.model.log.WebhookIncidentType;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.repository.CustomerSubscriptionRepository;
import realworld_backend.commerce.service.core.PaymentChannel;
import realworld_backend.commerce.service.core.PaymentChannelException;
import realworld_backend.commerce.service.core.PaymentChannelRouter;
import realworld_backend.commerce.service.entitlement.EntitlementProjector;
import realworld_backend.commerce.service.metrics.CommerceMetricsService;
import realworld_backend.commerce.service.statemachine.SubscriptionWebhookStateDecision;
import realworld_backend.commerce.service.statemachine.SubscriptionWebhookStateMachine;
import realworld_backend.commerce.service.statemachine.TransitionClass;
import realworld_backend.commerce.service.webhook.WebhookIncidentOrchestrator;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionStalledReconcileService {
    private static final int BATCH_SIZE = 100;
    private static final java.time.Duration PENDING_STALE_THRESHOLD = java.time.Duration.ofMinutes(30);
    private static final java.time.Duration PAYING_STALE_THRESHOLD = java.time.Duration.ofMinutes(15);

    private final CustomerSubscriptionRepository customerSubscriptionRepository;
    private final CustomerSubscriptionService customerSubscriptionService;
    private final PaymentChannelRouter paymentChannelRouter;
    private final SubscriptionWebhookStateMachine subscriptionWebhookStateMachine;
    private final EntitlementProjector entitlementProjector;
    private final WebhookIncidentOrchestrator webhookIncidentOrchestrator;
    private final CommerceMetricsService commerceMetricsService;

    @Transactional
    public void reconcileStalledSubscriptions() {
        Timer.Sample sample = commerceMetricsService.startTimer();
        try {
            reconcileStalePendingSubscriptions();
            reconcileStalePayingSubscriptions();
        } finally {
            commerceMetricsService.recordReconcileDuration(sample, "subscription_stalled");
        }
    }

    private void reconcileStalePendingSubscriptions() {
        LocalDateTime cutoff = UtcTimeMapper.nowUtc().minus(PENDING_STALE_THRESHOLD);
        List<CustomerSubscription> candidates =
                customerSubscriptionRepository.findTop100ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        List.of(SubscriptionStatus.PENDING),
                        cutoff
                );

        for (CustomerSubscription subscription : candidates) {
            if (subscription == null) {
                continue;
            }
            commerceMetricsService.recordReconcileScanned(SubscriptionStatus.PENDING);
            recordStalledIncident(
                    subscription,
                    "subscription stayed in PENDING beyond threshold; pending checkout lacks provider session id for active reconcile"
            );
        }
        if (candidates.size() >= BATCH_SIZE) {
            log.info("subscription stalled reconcile hit pending batch limit={}, more candidates may remain", BATCH_SIZE);
        }
    }

    private void reconcileStalePayingSubscriptions() {
        LocalDateTime cutoff = UtcTimeMapper.nowUtc().minus(PAYING_STALE_THRESHOLD);
        List<CustomerSubscription> candidates =
                customerSubscriptionRepository.findTop100ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        List.of(SubscriptionStatus.PAYING),
                        cutoff
                );

        for (CustomerSubscription subscription : candidates) {
            if (subscription != null) {
                commerceMetricsService.recordReconcileScanned(SubscriptionStatus.PAYING);
            }
            reconcilePayingSubscription(subscription);
        }
        if (candidates.size() >= BATCH_SIZE) {
            log.info("subscription stalled reconcile hit paying batch limit={}, more candidates may remain", BATCH_SIZE);
        }
    }

    private void reconcilePayingSubscription(CustomerSubscription subscription) {
        if (subscription == null || subscription.getSubscriptionNo() == null) {
            return;
        }
        if (!hasText(subscription.getProviderSubscriptionId())) {
            recordStalledIncident(
                    subscription,
                    "subscription stayed in PAYING beyond threshold without providerSubscriptionId"
            );
            return;
        }

        try {
            PaymentChannel paymentChannel = paymentChannelRouter.get(String.valueOf(subscription.getProvider()));
            ProviderSubscription providerSubscription =
                    paymentChannel.retrieveSubscription(subscription.getProviderSubscriptionId());
            if (providerSubscription == null) {
                recordStalledIncident(subscription, "provider subscription reconcile returned null");
                return;
            }

            SubscriptionStatus targetStatus = mapProviderStatus(providerSubscription.getStatus());
            if (targetStatus == null) {
                recordStalledIncident(
                        subscription,
                        "provider subscription status not mapped during reconcile: " + providerSubscription.getStatus()
                );
                return;
            }

            SubscriptionWebhookStateDecision decision =
                    subscriptionWebhookStateMachine.evaluateProviderStatusTransition(
                            BusinessEventType.SUBSCRIPTION_UPDATED,
                            subscription.getStatus(),
                            targetStatus
                    );

            if (decision.transitionClass() != TransitionClass.LEGAL_TRANSITION || !decision.shouldPersist()) {
                recordStalledIncident(
                        subscription,
                        "provider subscription reconcile rejected transition: current="
                                + subscription.getStatus() + ", target=" + targetStatus + ", class=" + decision.transitionClass()
                );
                return;
            }

            boolean clearActiveKey = decision.nextStateNature() == realworld_backend.commerce.service.statemachine.StateNature.TERMINAL_STATE;
            int updated = customerSubscriptionService.updateFromProviderIfStatusChanged(
                    subscription.getSubscriptionNo(),
                    List.of(subscription.getStatus()),
                    decision.nextStatus(),
                    providerSubscription.currentPeriodStartInstant(),
                    providerSubscription.currentPeriodEndInstant(),
                    providerSubscription.getCancelAtPeriodEnd(),
                    clearActiveKey,
                    Instant.now()
            );
            if (updated != 1) {
                recordStalledIncident(subscription, "provider subscription reconcile lost compare-and-set update");
                return;
            }
            commerceMetricsService.recordSubscriptionTransition(
                    "reconcile",
                    subscription.getStatus(),
                    decision.nextStatus()
            );
            commerceMetricsService.recordReconcileFixed(subscription.getStatus(), decision.nextStatus());

            CustomerSubscription refreshed = customerSubscriptionService.findBySubscriptionNo(subscription.getSubscriptionNo())
                    .orElse(subscription);
            refreshed.setProviderSubscriptionId(providerSubscription.getId());
            refreshed.setProviderCustomerId(providerSubscription.getCustomer());
            if (providerSubscription.canceledAtUtc() != null) {
                refreshed.setCanceledAt(providerSubscription.canceledAtUtc());
            }
            customerSubscriptionService.save(refreshed);
            customerSubscriptionService.markLifecycleEventObservedIfNewer(
                    refreshed.getSubscriptionNo(),
                    Instant.now(),
                    Instant.now()
            );
            entitlementProjector.refreshSubscriptionEntitlement(refreshed, UtcTimeMapper.nowUtc());

            log.info(
                    "subscription stalled reconcile advanced status, subscriptionNo={}, fromStatus={}, toStatus={}",
                    refreshed.getSubscriptionNo(),
                    subscription.getStatus(),
                    refreshed.getStatus()
            );
        } catch (PaymentChannelException e) {
            recordStalledIncident(subscription, "provider subscription reconcile failed: " + e.getMessage());
        } catch (Exception e) {
            recordStalledIncident(subscription, "unexpected stalled subscription reconcile failure: " + e.getMessage());
        }
    }

    private void recordStalledIncident(CustomerSubscription subscription, String reason) {
        if (subscription == null) {
            return;
        }
        commerceMetricsService.recordReconcileIncident(subscription.getStatus());
        String trackingId = subscription.getSubscriptionNo();
        webhookIncidentOrchestrator.recordIncident(
                String.valueOf(subscription.getProvider()),
                AbnormalDomainType.SUBSCRIPTION,
                null,
                "SUBSCRIPTION_STALLED_RECONCILE",
                "subscription-stalled:" + trackingId + ":" + subscription.getStatus(),
                trackingId,
                subscription.getProviderSubscriptionId(),
                WebhookIncidentType.UNCLASSIFIED_SYSTEM_EXCEPTION,
                reason,
                reason,
                null
        );
        log.warn("subscription stalled reconcile incident recorded, subscriptionNo={}, status={}, reason={}",
                subscription.getSubscriptionNo(), subscription.getStatus(), reason);
    }

    private SubscriptionStatus mapProviderStatus(String providerStatus) {
        if (!hasText(providerStatus)) {
            return null;
        }
        return switch (providerStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "past_due", "unpaid", "incomplete", "incomplete_expired" -> SubscriptionStatus.PAST_DUE;
            case "paused" -> SubscriptionStatus.PAUSED;
            case "canceled" -> SubscriptionStatus.CANCELED;
            default -> null;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

