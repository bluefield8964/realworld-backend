package realworld_backend.commerce.service.subscription.checkout;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.checkoutPayment.CheckoutSessionWebhookEvent;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.SubscriptionHistory;
import realworld_backend.commerce.service.AbnormalOrchestrator;
import realworld_backend.commerce.service.entitlement.EntitlementProjector;
import realworld_backend.commerce.service.metrics.CommerceMetricsService;
import realworld_backend.commerce.service.webhook.PaymentFailureEscalationService;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.statemachine.SubscriptionWebhookStateDecision;
import realworld_backend.commerce.service.statemachine.SubscriptionWebhookStateMachine;
import realworld_backend.commerce.service.statemachine.StateNature;
import realworld_backend.commerce.service.statemachine.TransitionClass;
import realworld_backend.commerce.service.subscription.CustomerSubscriptionService;
import realworld_backend.commerce.service.subscription.SubscriptionHistoryService;
import realworld_backend.commerce.service.subscription.snapshot.SubscriptionSnapshotMergePolicy;
import realworld_backend.commerce.service.subscription.snapshot.SubscriptionSnapshotMergeService;
import realworld_backend.commerce.service.subscription.snapshot.SubscriptionSnapshotPatch;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionCheckoutSessionWebhookService {
    private final RedissonClient redissonClient;
    private final AbnormalOrchestrator abnormalOrchestrator;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CustomerSubscriptionService customerSubscriptionService;
    private final SubscriptionHistoryService subscriptionHistoryService;
    private final PaymentFailureEscalationService paymentFailureEscalationService;
    private final SubscriptionWebhookStateMachine subscriptionWebhookStateMachine;
    private final SubscriptionSnapshotMergeService subscriptionSnapshotMergeService;
    private final EntitlementProjector entitlementProjector;
    private final CommerceMetricsService commerceMetricsService;

    public void handleSubscriptionSessionExpired(WebhookContext ctx, CheckoutSessionWebhookEvent.CheckoutSessionObject session) {
        prepareCheckoutContext(ctx, session);
        log.info(
                "handleSubscriptionSessionExpired: (subscription) accepted, eventId={}, trackingId={}",
                ctx.getEventId(),
                ctx.getTrackingId()
        );
        executeCheckoutTransition(
                ctx,
                session,
                BusinessEventType.CHECKOUT_SESSION_EXPIRED,
                "handleSubscriptionSessionExpired",
                applied -> {
                    log.info("handleSubscriptionSessionExpired: subscription checkout expired{}, subscriptionNo={}",
                            applied.afterRecheck() ? " after recheck" : "", applied.subscriptionNo());
                    appendCheckoutExpiredHistoryOrEscalate(
                            applied.subscriptionNo(),
                            applied.eventId(),
                            applied.providerObjectId(),
                            applied.idempotentKey(),
                            applied.ctx()
                    );
                },
                ignored -> {
                }
        );
    }

    public void handleSubscriptionCheckoutCompletedEvent(WebhookContext ctx, CheckoutSessionWebhookEvent.CheckoutSessionObject session) {
        prepareCheckoutContext(ctx, session);
        log.info("handleSubscriptionCheckoutCompletedEvent: (subscription) accepted, eventId={}, trackingId={}",
                ctx.getEventId(), ctx.getTrackingId());
        executeCheckoutTransition(
                ctx,
                session,
                BusinessEventType.CHECKOUT_SESSION_COMPLETED,
                "handleSubscriptionCheckoutCompletedEvent",
                applied -> {
                    log.info("handleSubscriptionCheckoutCompletedEvent: subscription moved to paying{}, subscriptionNo={}",
                            applied.afterRecheck() ? " after recheck" : "", applied.subscriptionNo());
                    appendPayingHistoryOrEscalate(
                            applied.subscriptionNo(),
                            applied.eventId(),
                            applied.providerObjectId(),
                            applied.idempotentKey(),
                            applied.ctx()
                    );
                },
                ignored -> {
                }
        );
    }

    public void handleSubscriptionCheckoutFailedEvent(
            WebhookContext ctx,
            CheckoutSessionWebhookEvent.CheckoutSessionObject session
    ) {
        prepareCheckoutContext(ctx, session);
        log.info(
                "handleSubscriptionCheckoutFailedEvent: (subscription) accepted, eventId={}, trackingId={}",
                ctx.getEventId(),
                ctx.getTrackingId()
        );
        executeCheckoutTransition(
                ctx,
                session,
                BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED,
                "handleSubscriptionCheckoutFailedEvent",
                applied -> {
                    log.info("handleSubscriptionCheckoutFailedEvent: subscription checkout failed{}, subscriptionNo={}",
                            applied.afterRecheck() ? " after recheck" : "", applied.subscriptionNo());
                    appendCheckoutFailHistoryOrEscalate(
                            applied.subscriptionNo(),
                            applied.eventId(),
                            applied.providerObjectId(),
                            applied.idempotentKey(),
                            applied.ctx()
                    );
                },
                ignored -> appendCheckoutFailHistoryIfPossible(ignored.subscriptionNo())
        );
    }

    private void prepareCheckoutContext(WebhookContext ctx, CheckoutSessionWebhookEvent.CheckoutSessionObject session) {
        String subscriptionNo = resolveSubscriptionNo(session);
        if (subscriptionNo == null || subscriptionNo.isBlank()) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }
        ctx.setTrackingId(subscriptionNo);
        ctx.setProviderTrackingId(session.getId());
    }

    private void executeCheckoutTransition(
            WebhookContext ctx,
            CheckoutSessionWebhookEvent.CheckoutSessionObject session,
            BusinessEventType transitionEventType,
            String capabilityName,
            CheckoutAppliedAction appliedAction,
            CheckoutIgnoredAction ignoredAction
    ) {
        String eventId = ctx.getEventId();
        String idempotentKey = "handleSubscription:event:" + eventId;
        String subscriptionNo = ctx.getTrackingId();
        if (subscriptionNo == null || subscriptionNo.isBlank()) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }

        RLock lock = redissonClient.getLock("subscription:lock:" + subscriptionNo);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);
            if (idempotencyLock != null) {
                log.info("{}: subscription already processed after lock, eventId={}, finish request",
                        capabilityName, eventId);
                return;
            }

            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("{}: Failed to acquire lock for subscription {}, finish request", capabilityName, subscriptionNo);
                throw new BizException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            CustomerSubscription currentSubscription = requireLocalSubscription(
                    subscriptionNo, session.getId(), eventId, idempotentKey, ctx
            );
            Instant eventCreatedAt = resolveProviderEventCreatedAt(ctx, session);
            if (isOlderThanLastCheckoutEvent(currentSubscription, eventCreatedAt)) {
                log.info("{}: stale checkout event ignored, subscriptionNo={}, eventId={}, eventCreatedAt={}, lastCheckoutEventCreatedAt={}",
                        capabilityName, subscriptionNo, eventId, eventCreatedAt, currentSubscription.getLastCheckoutEventCreatedAt());
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }
            SubscriptionWebhookStateDecision decision = subscriptionWebhookStateMachine.evaluate(
                    transitionEventType,
                    currentSubscription.getStatus()
            );
            if (decision.transitionClass() == TransitionClass.IGNORE_TRANSITION) {
                backfillSubscriptionSnapshot(subscriptionNo, session);
                markCheckoutEventObserved(subscriptionNo, eventCreatedAt);
                ignoredAction.onIgnored(new CheckoutTransitionContext(ctx, eventId, idempotentKey, session.getId(), subscriptionNo, false));
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }
            if (!decision.shouldPersist()) {
                handleSubscriptionIllegalDecision(ctx, decision, idempotentKey);
            }
            if (applySubscriptionTransitionDecision(subscriptionNo, decision, Instant.now())) {
                appliedAction.onApplied(new CheckoutTransitionContext(ctx, eventId, idempotentKey, session.getId(), subscriptionNo, false));
                backfillSubscriptionSnapshot(subscriptionNo, session);
                markCheckoutEventObserved(subscriptionNo, eventCreatedAt);
                refreshEntitlementProjection(subscriptionNo);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }

            currentSubscription = requireLocalSubscription(
                    subscriptionNo, session.getId(), eventId, idempotentKey, ctx
            );
            decision = subscriptionWebhookStateMachine.evaluate(
                    transitionEventType,
                    currentSubscription.getStatus()
            );
            if (decision.transitionClass() == TransitionClass.IGNORE_TRANSITION) {
                backfillSubscriptionSnapshot(subscriptionNo, session);
                markCheckoutEventObserved(subscriptionNo, eventCreatedAt);
                ignoredAction.onIgnored(new CheckoutTransitionContext(ctx, eventId, idempotentKey, session.getId(), subscriptionNo, true));
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }
            if (decision.shouldPersist() && applySubscriptionTransitionDecision(subscriptionNo, decision, Instant.now())) {
                appliedAction.onApplied(new CheckoutTransitionContext(ctx, eventId, idempotentKey, session.getId(), subscriptionNo, true));
                backfillSubscriptionSnapshot(subscriptionNo, session);
                markCheckoutEventObserved(subscriptionNo, eventCreatedAt);
                refreshEntitlementProjection(subscriptionNo);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }

            currentSubscription = requireLocalSubscription(
                    subscriptionNo, session.getId(), eventId, idempotentKey, ctx
            );
            decision = subscriptionWebhookStateMachine.evaluate(
                    transitionEventType,
                    currentSubscription.getStatus()
            );
            if (decision.transitionClass() == TransitionClass.IGNORE_TRANSITION) {
                backfillSubscriptionSnapshot(subscriptionNo, session);
                markCheckoutEventObserved(subscriptionNo, eventCreatedAt);
                ignoredAction.onIgnored(new CheckoutTransitionContext(ctx, eventId, idempotentKey, session.getId(), subscriptionNo, true));
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }
            handleSubscriptionIllegalDecision(ctx, decision, idempotentKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{}: Interrupted while acquiring lock, waiting for reconcile, subscriptionNo={}",
                    capabilityName, subscriptionNo);
            throw new BizException(ErrorCode.LOCK_INTERRUPTED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private CustomerSubscription requireLocalSubscription(
            String subscriptionNo,
            String providerObjectId,
            String eventId,
            String idempotentKey,
            WebhookContext ctx
    ) {
        return customerSubscriptionService.findBySubscriptionNo(subscriptionNo)
                .orElseThrow(() -> {
                    abnormalOrchestrator.upsertAbnormalOrder(
                            subscriptionNo,
                            eventId,
                            BusinessEventType.CUSTOMER_SUBSCRIPTION_MISSING,
                            providerObjectId,
                            "handleSubscription_CUSTOMER_SUBSCRIPTION_MISSING",
                            "CUSTOMER_SUBSCRIPTION_MISSING",
                            AbnormalOrderType.SUBSCRIPTION_MISSING,
                            ctx.getProvider(),
                            null
                    );
                    ctx.setAbnormalAlreadyUpserted(true);
                    redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                    return new BizException(ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND);
                });
    }

    private boolean applySubscriptionTransitionDecision(
            String subscriptionNo,
            SubscriptionWebhookStateDecision decision,
            Instant now
    ) {
        boolean clearActiveKey = decision.nextStateNature() == StateNature.TERMINAL_STATE;
        int updated = customerSubscriptionService.updateFromProviderIfStatusChanged(
                subscriptionNo,
                List.of(decision.currentStatus()),
                decision.nextStatus(),
                null,
                null,
                null,
                clearActiveKey,
                now
        );
        if (updated == 1) {
            commerceMetricsService.recordSubscriptionTransition(
                    "checkout",
                    decision.currentStatus(),
                    decision.nextStatus()
            );
            return true;
        }
        return false;
    }

    private void handleSubscriptionIllegalDecision(
            WebhookContext ctx,
            SubscriptionWebhookStateDecision decision,
            String idempotentKey
    ) {
        if (decision.transitionClass() == TransitionClass.RETRYABLE_ILLEGAL_TRANSITION) {
            log.warn(
                    "subscription checkout transition is retryable-illegal, eventType={}, eventId={}, trackingId={}, reason={}",
                    ctx.getEventType(),
                    ctx.getEventId(),
                    ctx.getTrackingId(),
                    decision.reason()
            );
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        if (decision.transitionClass() == TransitionClass.ILLEGAL_TRANSITION) {
            log.warn(
                    "subscription checkout transition is illegal, eventType={}, eventId={}, trackingId={}, reason={}",
                    ctx.getEventType(),
                    ctx.getEventId(),
                    ctx.getTrackingId(),
                    decision.reason()
            );
            paymentFailureEscalationService.recordIncidentForException(
                    ctx,
                    null,
                    null,
                    decision.reason(),
                    null,
                    null
            );
            redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
            throw new BizException(ErrorCode.SUBSCRIPTION_STATUS_CHANGE_FAIL);
        }
        throw new BizException(ErrorCode.SYSTEM_ERROR);
    }

    private void appendPayingHistoryOrEscalate(
            String subscriptionNo,
            String eventId,
            String providerObjectId,
            String idempotentKey,
            WebhookContext ctx
    ) {
        SubscriptionHistoryService.AppendResult appendResult =
                subscriptionHistoryService.appendPayingEventIfLatestPending(subscriptionNo);

        if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.HISTORY_MISSING) {
            Optional<SubscriptionHistory> bySessionId = subscriptionHistoryService.findBySubscription_subscriptionNo(subscriptionNo);
            if (bySessionId.isEmpty()) {
                abnormalOrchestrator.upsertAbnormalOrder(
                        subscriptionNo,
                        eventId,
                        BusinessEventType.SUBSCRIPTION_HISTORY_MISSING,
                        providerObjectId,
                        "handleSubscriptionCheckout_fail_subscriptionHistory",
                        "subscriptionHistory_change_to_INITIAL_FAIL",
                        AbnormalOrderType.SUBSCRIPTION_MISSING,
                        ctx.getProvider(),
                        null
                );
                ctx.setAbnormalAlreadyUpserted(true);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                throw new BizException(ErrorCode.SUBSCRIPTION_HISTORY_NOT_FOUND);
            }
        }
        if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.LATEST_PAYMENT_STATUS_MISMATCH) {
            log.warn("append paying history skipped due to latest payment status mismatch, subscriptionNo={}", subscriptionNo);
        }
    }

    private void appendCheckoutFailHistoryOrEscalate(
            String subscriptionNo,
            String eventId,
            String providerObjectId,
            String idempotentKey,
            WebhookContext ctx
    ) {
        SubscriptionHistoryService.AppendResult appendResult =
                subscriptionHistoryService.appendCheckoutFailEventIfLatestPaying(subscriptionNo);

        if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.HISTORY_MISSING) {
            Optional<SubscriptionHistory> bySessionId =
                    subscriptionHistoryService.findBySubscription_subscriptionNo(subscriptionNo);
            if (bySessionId.isEmpty()) {
                abnormalOrchestrator.upsertAbnormalOrder(
                        subscriptionNo,
                        eventId,
                        BusinessEventType.SUBSCRIPTION_HISTORY_MISSING,
                        providerObjectId,
                        "handleSubscriptionCheckout_fail_subscriptionHistory",
                        "subscriptionHistory_change_to_CHECKOUT_FAIL",
                        AbnormalOrderType.SUBSCRIPTION_MISSING,
                        ctx.getProvider(),
                        null
                );
                ctx.setAbnormalAlreadyUpserted(true);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                throw new BizException(ErrorCode.SUBSCRIPTION_HISTORY_NOT_FOUND);
            }
        }
        if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.LATEST_PAYMENT_STATUS_MISMATCH) {
            log.warn("append checkout-fail history skipped due to latest payment status mismatch, subscriptionNo={}", subscriptionNo);
        }
    }

    private void appendCheckoutFailHistoryIfPossible(String subscriptionNo) {
        SubscriptionHistoryService.AppendResult appendResult =
                subscriptionHistoryService.appendCheckoutFailEventIfLatestPaying(subscriptionNo);
        if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.LATEST_PAYMENT_STATUS_MISMATCH) {
            log.warn("idempotent path append checkout-fail skipped due to latest payment status mismatch, subscriptionNo={}", subscriptionNo);
        }
    }

    private void appendCheckoutExpiredHistoryOrEscalate(
            String subscriptionNo,
            String eventId,
            String providerObjectId,
            String idempotentKey,
            WebhookContext ctx
    ) {
        SubscriptionHistoryService.AppendResult appendResult =
                subscriptionHistoryService.appendCheckoutExpiredEventIfLatestPaying(subscriptionNo);

        if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.HISTORY_MISSING) {
            Optional<SubscriptionHistory> bySessionId =
                    subscriptionHistoryService.findBySubscription_subscriptionNo(subscriptionNo);
            if (bySessionId.isEmpty()) {
                abnormalOrchestrator.upsertAbnormalOrder(
                        subscriptionNo,
                        eventId,
                        BusinessEventType.SUBSCRIPTION_HISTORY_MISSING,
                        providerObjectId,
                        "handleSubscriptionCheckout_expired_subscriptionHistory",
                        "subscriptionHistory_change_to_CHECKOUT_EXPIRED",
                        AbnormalOrderType.SUBSCRIPTION_MISSING,
                        ctx.getProvider(),
                        null
                );
                ctx.setAbnormalAlreadyUpserted(true);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                throw new BizException(ErrorCode.SUBSCRIPTION_HISTORY_NOT_FOUND);
            }
        }
        if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.LATEST_PAYMENT_STATUS_MISMATCH) {
            log.warn("append checkout-expired history skipped due to latest payment status mismatch, subscriptionNo={}", subscriptionNo);
        }
    }

    private String resolveSubscriptionNo(CheckoutSessionWebhookEvent.CheckoutSessionObject session) {
        if (session == null) {
            return null;
        }

        Map<String, String> metadata = session.getMetadata();
        String subscriptionNo = metadata == null ? null : metadata.get("subscriptionNo");
        if (subscriptionNo != null && !subscriptionNo.isBlank()) {
            return subscriptionNo;
        }

        String orderNo = metadata == null ? null : metadata.get("orderNo");
        if (orderNo != null && !orderNo.isBlank()) {
            return orderNo;
        }

        String clientReferenceId = session.getClientReferenceId();
        if (clientReferenceId != null && !clientReferenceId.isBlank()) {
            return clientReferenceId;
        }

        String providerSubscriptionId = session.getSubscription();
        if (providerSubscriptionId != null && !providerSubscriptionId.isBlank()) {
            return customerSubscriptionService.findByProviderSubscriptionId(providerSubscriptionId)
                    .map(CustomerSubscription::getSubscriptionNo)
                    .orElse(null);
        }

        return null;
    }

    private void backfillSubscriptionSnapshot(
            String subscriptionNo,
            CheckoutSessionWebhookEvent.CheckoutSessionObject session
    ) {
        if (subscriptionNo == null || subscriptionNo.isBlank() || session == null) {
            return;
        }

        Optional<CustomerSubscription> localOptional = customerSubscriptionService.findBySubscriptionNo(subscriptionNo);
        if (localOptional.isEmpty()) {
            return;
        }

        CustomerSubscription local = localOptional.get();
        CheckoutSessionWebhookEvent.SubscriptionData subscriptionData = session.getSubscriptionData();
        SubscriptionSnapshotPatch patch = SubscriptionSnapshotPatch.builder()
                .providerSubscriptionId(session.getSubscription())
                .providerCustomerId(session.getCustomer())
                .currentPeriodStart(session.createdAtUtc())
                .currentPeriodEnd(subscriptionData == null ? null : subscriptionData.trialEndUtc())
                .build();

        boolean changed = subscriptionSnapshotMergeService.apply(
                local,
                patch,
                SubscriptionSnapshotMergePolicy.checkoutHint()
        );

        if (changed) {
            local.setUpdatedAt(UtcTimeMapper.nowUtc());
            customerSubscriptionService.save(local);
        }
    }

    private void refreshEntitlementProjection(String subscriptionNo) {
        customerSubscriptionService.findBySubscriptionNo(subscriptionNo)
                .ifPresent(subscription ->
                        entitlementProjector.refreshSubscriptionEntitlement(subscription, UtcTimeMapper.nowUtc()));
    }

    private Instant resolveProviderEventCreatedAt(
            WebhookContext ctx,
            CheckoutSessionWebhookEvent.CheckoutSessionObject session
    ) {
        if (ctx.getProviderRawEvent() != null && ctx.getProviderRawEvent().createdAtInstant() != null) {
            return ctx.getProviderRawEvent().createdAtInstant();
        }
        return session == null ? null : session.createdAtInstant();
    }

    private boolean isOlderThanLastCheckoutEvent(CustomerSubscription subscription, Instant eventCreatedAt) {
        if (subscription == null || eventCreatedAt == null || subscription.getLastCheckoutEventCreatedAt() == null) {
            return false;
        }
        return eventCreatedAt.isBefore(subscription.getLastCheckoutEventCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant());
    }

    private void markCheckoutEventObserved(String subscriptionNo, Instant eventCreatedAt) {
        if (subscriptionNo == null || subscriptionNo.isBlank() || eventCreatedAt == null) {
            return;
        }
        customerSubscriptionService.markCheckoutEventObservedIfNewer(subscriptionNo, eventCreatedAt, Instant.now());
    }

    @FunctionalInterface
    private interface CheckoutAppliedAction {
        void onApplied(CheckoutTransitionContext context);
    }

    @FunctionalInterface
    private interface CheckoutIgnoredAction {
        void onIgnored(CheckoutTransitionContext context);
    }

    private record CheckoutTransitionContext(
            WebhookContext ctx,
            String eventId,
            String idempotentKey,
            String providerObjectId,
            String subscriptionNo,
            boolean afterRecheck
    ) {
    }
}

