package realworld_backend.commerce.service.subscription.lifecycle;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.SubscriptionHistory;
import realworld_backend.commerce.model.subscription.SubscriptionWebhookEvent;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.service.AbnormalOrchestrator;
import realworld_backend.commerce.service.entitlement.EntitlementProjector;
import realworld_backend.commerce.service.metrics.CommerceMetricsService;
import realworld_backend.commerce.service.webhook.PaymentFailureEscalationService;
import realworld_backend.commerce.service.core.ProviderTimeMapper;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.webhook.parser.WebhookObjectParserRouter;
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
/**
 * Owns subscription lifecycle truth. This service handles provider lifecycle events
 * that may change the main {@link SubscriptionStatus}, and also backfills provider
 * snapshot fields after a successful lifecycle transition.
 */
public class SubscriptionLifecycleWebhookService {
    private final WebhookObjectParserRouter webhookObjectParserRouter;
    private final RedissonClient redissonClient;
    private final SubscriptionHistoryService subscriptionHistoryService;
    private final AbnormalOrchestrator abnormalOrchestrator;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CustomerSubscriptionService customerSubscriptionService;
    private final PaymentFailureEscalationService paymentFailureEscalationService;
    private final SubscriptionWebhookStateMachine subscriptionWebhookStateMachine;
    private final SubscriptionSnapshotMergeService subscriptionSnapshotMergeService;
    private final EntitlementProjector entitlementProjector;
    private final CommerceMetricsService commerceMetricsService;

    @Transactional
    public void handleSubscriptionDeletedEvent(WebhookContext ctx) {
        ResolvedLifecycleEvent resolved = parseLifecycleEventAndBindContext(ctx);
        String eventId = ctx.getEventId();
        String idempotentKey = "handleSubscription:event:" + eventId;
        if (!hasText(resolved.subscriptionNo())) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }
        executeLifecycleTransition(
                ctx,
                resolved.subscriptionObject(),
                BusinessEventType.SUBSCRIPTION_DELETED,
                idempotentKey,
                "handleSubscriptionDeletedEvent",
                "handleSubscriptionDeleted_CUSTOMER_SUBSCRIPTION_MISSING",
                true,
                null,
                applied -> {
                    log.info("handleSubscriptionDeletedEvent: subscription cancellation applied{}, subscriptionNo={}",
                            applied.afterRecheck() ? " after recheck" : "", applied.subscriptionNo());
                    appendCanceledHistoryOrEscalate(
                            applied.subscriptionNo(),
                            applied.eventId(),
                            applied.providerObjectId(),
                            applied.idempotentKey(),
                            applied.ctx()
                    );
                }
        );
    }

    @Transactional
    public void handleSubscriptionCreatedEvent(WebhookContext ctx) {
        ResolvedLifecycleEvent resolved = parseLifecycleEventAndBindContext(ctx);
        SubscriptionWebhookEvent.SubscriptionObject subscriptionObject = resolved.subscriptionObject();
        String providerSubscriptionId = resolved.providerSubscriptionId();
        Map<String, String> metadata = subscriptionObject.getMetadata();
        String userId = metadata == null ? null : metadata.get("userId");
        String subscriptionNo = resolved.subscriptionNo();
        String product = metadata == null ? null : metadata.get("product");
        if (!hasText(subscriptionNo)) {
            log.error("subscription mapping missing: subscriptionNo, providerSubscriptionId={}", providerSubscriptionId);
            throw new BizException(ErrorCode.JSON_ERROR);
        }

        if (userId == null || userId.isBlank()) {
            log.warn("subscription metadata missing userId, fallback mapping by providerSubscriptionId, subscriptionNo={}, providerSubscriptionId={}",
                    subscriptionNo, providerSubscriptionId);
        }

        log.info(
                "Subscription created event accepted: subscriptionNo={}, product={}, providerSubscriptionId={}",
                subscriptionNo,
                product,
                providerSubscriptionId
        );
        SubscriptionStatus targetStatus = mapProviderStatus(subscriptionObject.getStatus());
        executeLifecycleTransition(
                ctx,
                subscriptionObject,
                BusinessEventType.SUBSCRIPTION_CREATED,
                "handleSubscription:event:" + ctx.getEventId(),
                "activateSubscriptionFromWebhook",
                "handleSubscription_CUSTOMER_SUBSCRIPTION_MISSING",
                false,
                targetStatus,
                applied -> {
                    log.info("activateSubscriptionFromWebhook: provider status applied{}, subscriptionNo={}, nextStatus={}",
                            applied.afterRecheck() ? " after recheck" : "", applied.subscriptionNo(), applied.nextStatus());
                    if (applied.nextStatus() == SubscriptionStatus.ACTIVE || applied.nextStatus() == SubscriptionStatus.TRIALING) {
                        appendActiveHistoryOrEscalate(
                                applied.subscriptionNo(),
                                applied.eventId(),
                                applied.providerObjectId(),
                                applied.idempotentKey(),
                                applied.provider(),
                                applied.ctx()
                        );
                    } else if (applied.nextStatus() == SubscriptionStatus.CANCELED) {
                        appendCanceledHistoryOrEscalate(
                                applied.subscriptionNo(),
                                applied.eventId(),
                                applied.providerObjectId(),
                                applied.idempotentKey(),
                                applied.ctx()
                        );
                    }
                }
        );
    }

    @Transactional
    public void handleSubscriptionLifecycleUpdatedEvent(WebhookContext ctx) {
        ResolvedLifecycleEvent resolved = parseLifecycleEventAndBindContext(ctx);
        SubscriptionWebhookEvent.SubscriptionObject subscriptionObject = resolved.subscriptionObject();
        String subscriptionNo = resolved.subscriptionNo();
        if (!hasText(subscriptionNo)) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }
        SubscriptionStatus targetStatus = mapProviderStatus(subscriptionObject.getStatus());
        executeLifecycleTransition(
                ctx,
                subscriptionObject,
                BusinessEventType.SUBSCRIPTION_UPDATED,
                "handleSubscription:event:" + ctx.getEventId(),
                "customer.subscription.updated",
                "handleSubscriptionLifecycleUpdated_CUSTOMER_SUBSCRIPTION_MISSING",
                false,
                targetStatus,
                applied -> log.info("customer.subscription.updated applied{}, subscriptionNo={}, nextStatus={}",
                        applied.afterRecheck() ? " after recheck" : "", applied.subscriptionNo(), applied.nextStatus())
        );
    }

    @Transactional
    public void handleSubscriptionPausedEvent(WebhookContext ctx) {
        ResolvedLifecycleEvent resolved = parseLifecycleEventAndBindContext(ctx);
        if (!hasText(resolved.subscriptionNo())) {
            log.error("customer.subscription.paused missing subscriptionNo, providerSubscriptionId={}", resolved.providerSubscriptionId());
            throw new BizException(ErrorCode.JSON_ERROR);
        }

        executeLifecycleTransition(
                ctx,
                resolved.subscriptionObject(),
                BusinessEventType.SUBSCRIPTION_PAUSED,
                "handleSubscription:event:" + ctx.getEventId(),
                "customer.subscription.paused",
                "handleSubscription_CUSTOMER_SUBSCRIPTION_MISSING",
                false,
                null,
                applied -> log.info("customer.subscription.paused applied{}, subscriptionNo={}",
                        applied.afterRecheck() ? " after recheck" : "", applied.subscriptionNo())
        );
    }

    @Transactional
    public void handleSubscriptionResumedEvent(WebhookContext ctx) {
        ResolvedLifecycleEvent resolved = parseLifecycleEventAndBindContext(ctx);
        if (!hasText(resolved.subscriptionNo())) {
            log.error("customer.subscription.resumed missing subscriptionNo, providerSubscriptionId={}", resolved.providerSubscriptionId());
            throw new BizException(ErrorCode.JSON_ERROR);
        }

        executeLifecycleTransition(
                ctx,
                resolved.subscriptionObject(),
                BusinessEventType.SUBSCRIPTION_RESUMED,
                "handleSubscription:event:" + ctx.getEventId(),
                "customer.subscription.resumed",
                "handleSubscription_CUSTOMER_SUBSCRIPTION_MISSING",
                false,
                null,
                applied -> log.info("customer.subscription.resumed applied{}, subscriptionNo={}",
                        applied.afterRecheck() ? " after recheck" : "", applied.subscriptionNo())
        );
    }

    private void executeLifecycleTransition(
            WebhookContext ctx,
            SubscriptionWebhookEvent.SubscriptionObject subscriptionObject,
            BusinessEventType businessEventType,
            String idempotentKey,
            String capabilityName,
            String missingSource,
            boolean deletedEvent,
            SubscriptionStatus targetStatus,
            LifecycleAppliedAction appliedAction
    ) {
        String eventId = ctx.getEventId();
        String subscriptionNo = ctx.getTrackingId();
        if (subscriptionNo == null || subscriptionNo.isBlank()) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }
        String providerSubscriptionId = subscriptionObject.getId();
        Instant providerPeriodStart = subscriptionObject.currentPeriodStartInstant();
        Instant providerPeriodEnd = subscriptionObject.currentPeriodEndInstant();
        Instant providerEventCreatedAt = resolveProviderEventCreatedAt(ctx, subscriptionObject);
        Instant now = Instant.now();

        RLock lock = redissonClient.getLock("subscription:lock:" + subscriptionNo);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);

            if (idempotencyLock != null) {
                log.info("{}: subscription already processed after lock, eventId={}, finish request", capabilityName, eventId);
                return;
            }

            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("{}: failed to acquire lock for subscription {}, finish request", capabilityName, subscriptionNo);
                throw new BizException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            CustomerSubscription currentSubscription = requireLocalSubscription(
                    subscriptionNo,
                    providerSubscriptionId,
                    eventId,
                    idempotentKey,
                    ctx,
                    missingSource
            );
            if (isOlderThanLastLifecycleEvent(currentSubscription, providerEventCreatedAt)) {
                log.info("{}: stale lifecycle event ignored, subscriptionNo={}, eventId={}, eventCreatedAt={}, lastLifecycleEventCreatedAt={}",
                        capabilityName, subscriptionNo, eventId, providerEventCreatedAt, currentSubscription.getLastLifecycleEventCreatedAt());
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }
            SubscriptionWebhookStateDecision decision = evaluateLifecycleDecision(
                    businessEventType,
                    currentSubscription.getStatus(),
                    targetStatus
            );
            if (decision.transitionClass() == TransitionClass.IGNORE_TRANSITION) {
                backfillProviderIdentityAndDeleteMeta(subscriptionNo, subscriptionObject, deletedEvent);
                markLifecycleEventObserved(subscriptionNo, providerEventCreatedAt);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }
            if (!decision.shouldPersist()) {
                handleSubscriptionIllegalDecision(ctx, decision, idempotentKey);
            }

            if (applySubscriptionTransitionDecision(
                    subscriptionNo,
                    decision,
                    providerPeriodStart,
                    providerPeriodEnd,
                    subscriptionObject.getCancelAtPeriodEnd(),
                    now
            )) {
                appliedAction.onApplied(new LifecycleAppliedContext(
                        ctx,
                        eventId,
                        idempotentKey,
                        providerSubscriptionId,
                        subscriptionNo,
                        ctx.getProvider(),
                        decision.nextStatus(),
                        false
                ));
                backfillProviderIdentityAndDeleteMeta(subscriptionNo, subscriptionObject, deletedEvent);
                markLifecycleEventObserved(subscriptionNo, providerEventCreatedAt);
                refreshEntitlementProjection(subscriptionNo);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }

            currentSubscription = requireLocalSubscription(
                    subscriptionNo,
                    providerSubscriptionId,
                    eventId,
                    idempotentKey,
                    ctx,
                    missingSource
            );
            decision = evaluateLifecycleDecision(
                    businessEventType,
                    currentSubscription.getStatus(),
                    targetStatus
            );
            if (decision.transitionClass() == TransitionClass.IGNORE_TRANSITION) {
                backfillProviderIdentityAndDeleteMeta(subscriptionNo, subscriptionObject, deletedEvent);
                markLifecycleEventObserved(subscriptionNo, providerEventCreatedAt);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }
            if (decision.shouldPersist() && applySubscriptionTransitionDecision(
                    subscriptionNo,
                    decision,
                    providerPeriodStart,
                    providerPeriodEnd,
                    subscriptionObject.getCancelAtPeriodEnd(),
                    now
            )) {
                appliedAction.onApplied(new LifecycleAppliedContext(
                        ctx,
                        eventId,
                        idempotentKey,
                        providerSubscriptionId,
                        subscriptionNo,
                        ctx.getProvider(),
                        decision.nextStatus(),
                        true
                ));
                backfillProviderIdentityAndDeleteMeta(subscriptionNo, subscriptionObject, deletedEvent);
                markLifecycleEventObserved(subscriptionNo, providerEventCreatedAt);
                refreshEntitlementProjection(subscriptionNo);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }

            currentSubscription = requireLocalSubscription(
                    subscriptionNo,
                    providerSubscriptionId,
                    eventId,
                    idempotentKey,
                    ctx,
                    missingSource
            );
            decision = evaluateLifecycleDecision(
                    businessEventType,
                    currentSubscription.getStatus(),
                    targetStatus
            );
            if (decision.transitionClass() == TransitionClass.IGNORE_TRANSITION) {
                backfillProviderIdentityAndDeleteMeta(subscriptionNo, subscriptionObject, deletedEvent);
                markLifecycleEventObserved(subscriptionNo, providerEventCreatedAt);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                return;
            }
            handleSubscriptionIllegalDecision(ctx, decision, idempotentKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{}: interrupted while acquiring lock, waiting for reconcile, subscriptionNo={}", capabilityName, subscriptionNo);
            throw new BizException(ErrorCode.LOCK_INTERRUPTED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private SubscriptionWebhookStateDecision evaluateLifecycleDecision(
            BusinessEventType eventType,
            SubscriptionStatus currentStatus,
            SubscriptionStatus targetStatus
    ) {
        if (eventType == BusinessEventType.SUBSCRIPTION_CREATED
                || eventType == BusinessEventType.SUBSCRIPTION_UPDATED) {
            return subscriptionWebhookStateMachine.evaluateProviderStatusTransition(
                    eventType,
                    currentStatus,
                    targetStatus
            );
        }
        return subscriptionWebhookStateMachine.evaluate(eventType, currentStatus);
    }

    private ResolvedLifecycleEvent parseLifecycleEventAndBindContext(WebhookContext ctx) {
        SubscriptionWebhookEvent event =
                webhookObjectParserRouter.parseAs(
                        ctx.getProviderRawEvent(),
                        ctx.getEventType(),
                        SubscriptionWebhookEvent.class
                );
        ctx.setProviderEvent(event);
        SubscriptionWebhookEvent.SubscriptionObject subscriptionObject = event.getObject();
        if (subscriptionObject == null) {
            throw new BizException(ErrorCode.SUBSCRIPTION_SESSION_NOT_FOUND);
        }
        String subscriptionNo = resolveSubscriptionNo(subscriptionObject);
        String providerSubscriptionId = subscriptionObject.getId();
        ctx.setTrackingId(subscriptionNo);
        ctx.setProviderTrackingId(providerSubscriptionId);
        return new ResolvedLifecycleEvent(event, subscriptionObject, subscriptionNo, providerSubscriptionId);
    }

    private Instant resolveProviderEventCreatedAt(
            WebhookContext ctx,
            SubscriptionWebhookEvent.SubscriptionObject subscriptionObject
    ) {
        if (ctx.getProviderRawEvent() != null && ctx.getProviderRawEvent().createdAtInstant() != null) {
            return ctx.getProviderRawEvent().createdAtInstant();
        }
        return subscriptionObject == null ? null : subscriptionObject.createdAtInstant();
    }

    private boolean isOlderThanLastLifecycleEvent(CustomerSubscription subscription, Instant eventCreatedAt) {
        if (subscription == null || eventCreatedAt == null || subscription.getLastLifecycleEventCreatedAt() == null) {
            return false;
        }
        return eventCreatedAt.isBefore(subscription.getLastLifecycleEventCreatedAt().atZone(java.time.ZoneOffset.UTC).toInstant());
    }

    private void markLifecycleEventObserved(String subscriptionNo, Instant eventCreatedAt) {
        if (!hasText(subscriptionNo) || eventCreatedAt == null) {
            return;
        }
        customerSubscriptionService.markLifecycleEventObservedIfNewer(subscriptionNo, eventCreatedAt, Instant.now());
    }

    private SubscriptionStatus mapProviderStatus(String providerStatus) {
        if (providerStatus == null || providerStatus.isBlank()) {
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

    private String resolveSubscriptionNo(SubscriptionWebhookEvent.SubscriptionObject subscriptionObject) {
        if (subscriptionObject == null) {
            return null;
        }

        Map<String, String> metadata = subscriptionObject.getMetadata();
        String subscriptionNo = metadata == null ? null : metadata.get("subscriptionNo");
        if (subscriptionNo != null && !subscriptionNo.isBlank()) {
            return subscriptionNo;
        }

        return findCustomerSubscription(subscriptionObject)
                .map(CustomerSubscription::getSubscriptionNo)
                .orElse(null);
    }

    private Optional<CustomerSubscription> findCustomerSubscription(
            SubscriptionWebhookEvent.SubscriptionObject subscriptionObject
    ) {
        if (subscriptionObject == null) {
            return Optional.empty();
        }

        String providerSubscriptionId = subscriptionObject.getId();
        if (providerSubscriptionId == null || providerSubscriptionId.isBlank()) {
            return Optional.empty();
        }

        return customerSubscriptionService.findByProviderSubscriptionId(providerSubscriptionId);
    }

    private void backfillProviderIdentityAndDeleteMeta(
            String subscriptionNo,
            SubscriptionWebhookEvent.SubscriptionObject subscriptionObject,
            boolean deletedEvent
    ) {
        Optional<CustomerSubscription> localOptional = customerSubscriptionService.findBySubscriptionNo(subscriptionNo);
        if (localOptional.isEmpty()) {
            return;
        }

        CustomerSubscription local = localOptional.get();
        LocalDateTime providerCanceledAt = null;
        if (deletedEvent) {
            providerCanceledAt = ProviderTimeMapper.toUtcLocalDateTime(
                    ProviderTimeMapper.toInstant(subscriptionObject.getCanceledAt())
            );
            if (providerCanceledAt == null) {
                providerCanceledAt = UtcTimeMapper.nowUtc();
            }
        }

        SubscriptionSnapshotPatch patch = SubscriptionSnapshotPatch.builder()
                .providerSubscriptionId(subscriptionObject.getId())
                .providerCustomerId(subscriptionObject.getCustomer())
                .currentPeriodStart(subscriptionObject.currentPeriodStartUtc())
                .currentPeriodEnd(subscriptionObject.currentPeriodEndUtc())
                .cancelAtPeriodEnd(subscriptionObject.getCancelAtPeriodEnd())
                .canceledAt(providerCanceledAt)
                .build();

        boolean changed = subscriptionSnapshotMergeService.apply(
                local,
                patch,
                SubscriptionSnapshotMergePolicy.lifecycleAuthoritative()
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

    private CustomerSubscription requireLocalSubscription(
            String subscriptionNo,
            String providerObjectId,
            String eventId,
            String idempotentKey,
            WebhookContext ctx,
            String source
    ) {
        return customerSubscriptionService.findBySubscriptionNo(subscriptionNo)
                .orElseThrow(() -> {
                    abnormalOrchestrator.upsertAbnormalOrder(
                            subscriptionNo,
                            eventId,
                            ctx.getEventType(),
                            providerObjectId,
                            source,
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
            Instant providerPeriodStart,
            Instant providerPeriodEnd,
            Boolean cancelAtPeriodEnd,
            Instant now
    ) {
        boolean clearActiveKey = decision.nextStateNature() == StateNature.TERMINAL_STATE;
        int updated = customerSubscriptionService.updateFromProviderIfStatusChanged(
                subscriptionNo,
                List.of(decision.currentStatus()),
                decision.nextStatus(),
                providerPeriodStart,
                providerPeriodEnd,
                cancelAtPeriodEnd,
                clearActiveKey,
                now
        );
        if (updated == 1) {
            commerceMetricsService.recordSubscriptionTransition(
                    "lifecycle",
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
                    "subscription transition is retryable-illegal, eventType={}, eventId={}, trackingId={}, reason={}",
                    ctx.getEventType(),
                    ctx.getEventId(),
                    ctx.getTrackingId(),
                    decision.reason()
            );
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        if (decision.transitionClass() == TransitionClass.ILLEGAL_TRANSITION) {
            log.warn(
                    "subscription transition is illegal, eventType={}, eventId={}, trackingId={}, reason={}",
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

    private void appendCanceledHistoryOrEscalate(
            String subscriptionNo,
            String eventId,
            String providerObjectId,
            String idempotentKey,
            WebhookContext ctx
    ) {
        SubscriptionHistoryService.AppendResult appendResult =
                subscriptionHistoryService.appendCanceledEventIfLatestPaid(subscriptionNo);
        if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.HISTORY_MISSING) {
            Optional<SubscriptionHistory> bySessionId = subscriptionHistoryService.findBySubscription_subscriptionNo(subscriptionNo);
            if (bySessionId.isEmpty()) {
                abnormalOrchestrator.upsertAbnormalOrder(
                        subscriptionNo,
                        eventId,
                        BusinessEventType.SUBSCRIPTION_HISTORY_MISSING,
                        providerObjectId,
                        "handleSubscriptionDeleted_fail_subscriptionHistory",
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
            log.warn("append canceled history skipped due to latest payment status mismatch, subscriptionNo={}", subscriptionNo);
        }
    }

    private void appendActiveHistoryOrEscalate(
            String subscriptionNo,
            String eventId,
            String providerObjectId,
            String idempotentKey,
            String provider,
            WebhookContext ctx
    ) {
        SubscriptionHistoryService.AppendResult appendResult =
                subscriptionHistoryService.appendActiveEventIfLatestPaid(subscriptionNo);
        if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.HISTORY_MISSING) {
            Optional<SubscriptionHistory> bySessionId = subscriptionHistoryService.findBySubscription_subscriptionNo(subscriptionNo);
            if (bySessionId.isEmpty()) {
                abnormalOrchestrator.upsertAbnormalOrder(
                        subscriptionNo,
                        eventId,
                        BusinessEventType.SUBSCRIPTION_HISTORY_MISSING,
                        providerObjectId,
                        "handleSubscription_fail_subscriptionHistory_missing",
                        "subscriptionHistory_missing",
                        AbnormalOrderType.SUBSCRIPTION_HISTORY_CHANGE_FAIL,
                        provider,
                        null
                );
                ctx.setAbnormalAlreadyUpserted(true);
                redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
                throw new BizException(ErrorCode.SUBSCRIPTION_HISTORY_NOT_FOUND);
            }
        }
        if (appendResult.outcome() == SubscriptionHistoryService.AppendOutcome.LATEST_PAYMENT_STATUS_MISMATCH) {
            log.warn("append active history skipped due to latest payment status mismatch, subscriptionNo={}", subscriptionNo);
        }
    }

    @FunctionalInterface
    private interface LifecycleAppliedAction {
        void onApplied(LifecycleAppliedContext context);
    }

    private record LifecycleAppliedContext(
            WebhookContext ctx,
            String eventId,
            String idempotentKey,
            String providerObjectId,
            String subscriptionNo,
            String provider,
            SubscriptionStatus nextStatus,
            boolean afterRecheck
    ) {
    }

    private record ResolvedLifecycleEvent(
            SubscriptionWebhookEvent event,
            SubscriptionWebhookEvent.SubscriptionObject subscriptionObject,
            String subscriptionNo,
            String providerSubscriptionId
    ) {
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

