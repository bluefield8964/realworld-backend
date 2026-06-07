package realworld_backend.commerce.service.subscription.lifecycle;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.commerce.model.exception.WebhookDuplicateIgnoredException;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.SubscriptionWebhookEvent;
import realworld_backend.commerce.service.AbnormalOrchestrator;
import realworld_backend.commerce.service.core.ProviderTimeMapper;
import realworld_backend.commerce.service.webhook.core.WebhookContext;
import realworld_backend.commerce.service.webhook.parser.WebhookObjectParserRouter;
import realworld_backend.commerce.service.subscription.CustomerSubscriptionService;
import realworld_backend.commerce.service.subscription.snapshot.SubscriptionSnapshotMergePolicy;
import realworld_backend.commerce.service.subscription.snapshot.SubscriptionSnapshotMergeService;
import realworld_backend.commerce.service.subscription.snapshot.SubscriptionSnapshotPatch;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Applies pure subscription snapshot events that do not own lifecycle state transitions.
 * Subscription created/updated/deleted lifecycle events should be handled by
 * {@link SubscriptionLifecycleWebhookService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionSnapshotSyncService {
    private final WebhookObjectParserRouter webhookObjectParserRouter;
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CustomerSubscriptionService customerSubscriptionService;
    private final AbnormalOrchestrator abnormalOrchestrator;
    private final SubscriptionSnapshotMergeService subscriptionSnapshotMergeService;


    @Transactional
    public void handleSubscriptionTrialWillEndEvent(WebhookContext ctx) {
        SubscriptionWebhookEvent event =
                webhookObjectParserRouter.parseAs(
                        ctx.getProviderRawEvent(),
                        ctx.getEventType(),
                        SubscriptionWebhookEvent.class
                );
        ctx.setProviderEvent(event);
        String eventId = ctx.getEventId();
        String idempotentKey = "handleSubscription:event:" + eventId;
        SubscriptionWebhookEvent.SubscriptionObject subscriptionObject = event == null ? null : event.getObject();
        if (subscriptionObject == null) {
            throw new BizException(ErrorCode.SUBSCRIPTION_SESSION_NOT_FOUND);
        }

        String subscriptionNo = resolveSubscriptionNo(subscriptionObject);
        String providerSubscriptionId = subscriptionObject.getId();
        ctx.setTrackingId(subscriptionNo);
        ctx.setProviderTrackingId(providerSubscriptionId);
        if (!hasText(subscriptionNo)) {
            upsertSubscriptionMissingAbnormalOrder(null, providerSubscriptionId, eventId, idempotentKey, ctx);
            throw new BizException(ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND);
        }

        RLock lock = redissonClient.getLock("subscription:lock:" + subscriptionNo);
        try {
            Object idempotencyLock = redisTemplate.opsForValue().get(idempotentKey);
            if (idempotencyLock != null) {
                log.info("subscription snapshot already processed after lock, eventType={}, eventId={}",
                        ctx.getEventType(), eventId);
                throw new WebhookDuplicateIgnoredException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("subscription snapshot failed to acquire lock, eventType={}, subscriptionNo={}",
                        ctx.getEventType(), subscriptionNo);
                throw new WebhookDuplicateIgnoredException(ErrorCode.LOCK_CANNOT_ACQUIRE);
            }

            boolean synced = syncSubscriptionSnapshot(subscriptionNo, subscriptionObject);
            if (!synced) {
                upsertSubscriptionMissingAbnormalOrder(subscriptionNo, providerSubscriptionId, eventId, idempotentKey, ctx);
                throw new BizException(ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND);
            }

            redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
            log.info(
                    "subscription snapshot accepted, eventType={}, subscriptionNo={}, providerSubscriptionId={}",
                    ctx.getEventType(),
                    subscriptionNo,
                    providerSubscriptionId
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("subscription snapshot interrupted while acquiring lock, eventType={}, subscriptionNo={}",
                    ctx.getEventType(), subscriptionNo);
            throw new BizException(ErrorCode.LOCK_INTERRUPTED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private boolean syncSubscriptionSnapshot(
            String subscriptionNo,
            SubscriptionWebhookEvent.SubscriptionObject subscriptionObject
    ) {
        if (!hasText(subscriptionNo) || subscriptionObject == null) {
            return false;
        }
        Optional<CustomerSubscription> localOptional = customerSubscriptionService.findBySubscriptionNo(subscriptionNo);
        if (localOptional.isEmpty()) {
            return false;
        }

        CustomerSubscription local = localOptional.get();
        LocalDateTime providerCanceledAt = ProviderTimeMapper.toUtcLocalDateTime(
                ProviderTimeMapper.toInstant(subscriptionObject.getCanceledAt())
        );
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
                SubscriptionSnapshotMergePolicy.snapshotConservative()
        );
        if (changed) {
            local.setUpdatedAt(UtcTimeMapper.nowUtc());
            customerSubscriptionService.save(local);
        }
        return true;
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

    private void upsertSubscriptionMissingAbnormalOrder(
            String subscriptionNo,
            String providerObjectId,
            String eventId,
            String idempotentKey,
            WebhookContext ctx
    ) {
        abnormalOrchestrator.upsertAbnormalOrder(
                subscriptionNo,
                eventId,
                ctx.getEventType(),
                providerObjectId,
                "handleSubscriptionSnapshot_CUSTOMER_SUBSCRIPTION_MISSING",
                "CUSTOMER_SUBSCRIPTION_MISSING",
                AbnormalOrderType.SUBSCRIPTION_MISSING,
                ctx.getProvider(),
                null
        );

        ctx.setAbnormalAlreadyUpserted(true);

        redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

