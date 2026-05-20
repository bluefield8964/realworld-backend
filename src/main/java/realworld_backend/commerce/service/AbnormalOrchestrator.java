package realworld_backend.commerce.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.log.AbnormalDomainType;
import realworld_backend.commerce.model.log.AbnormalOrder;
import realworld_backend.commerce.model.log.AbnormalOrderStatus;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.repository.AbnormalOrderRepository;
import realworld_backend.commerce.service.core.PaymentChannelException;
import realworld_backend.commerce.service.order.OrderAbnormalService;
import realworld_backend.commerce.service.subscription.SubscriptionAbnormalService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AbnormalOrchestrator {
    private static final int MAX_RETRY_COUNT = 5;
    private static final Duration RECONCILING_LEASE_TIMEOUT = Duration.ofMinutes(3);
    private final Duration processingStale = Duration.ofSeconds(30);

    private final AbnormalOrderRepository abnormalOrderRepository;
    private final RedissonClient redissonClient;
    private final OrderAbnormalService orderAbnormalService;
    private final SubscriptionAbnormalService subscriptionAbnormalService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertAbnormalOrder(
            String orderNo,
            String eventId,
            BusinessEventType eventType,
            String sessionId,
            String reason,
            String errorMessage,
            AbnormalOrderType abnormalType,
            String provider,
            String requestId
    ) {
        LocalDateTime now = LocalDateTime.now();
        String errorLine = "[" + now + "] " + errorMessage;
        AbnormalDomainType domainType = resolveDomainType(eventType);
        String normalizedOrderNo = resolveAbnormalOrderNo(orderNo);
        String normalizedSessionId = resolveAbnormalSessionId(sessionId);
        AbnormalOrderStatus initialStatus = resolveInitialStatus(abnormalType);
        Optional<AbnormalOrder> existing = lockExistingAbnormal(normalizedSessionId);
        if (existing.isPresent()) {
            AbnormalOrder abnormalOrder = existing.get();
            if (isTerminalStatus(abnormalOrder.getStatus())) {
                return;
            }
            mergeDuplicateAbnormal(
                    abnormalOrder,
                    normalizedOrderNo,
                    eventId,
                    eventType,
                    normalizedSessionId,
                    requestId,
                    provider,
                    domainType,
                    reason,
                    errorLine,
                    abnormalType,
                    now
            );
            abnormalOrderRepository.save(abnormalOrder);
            return;
        }

        AbnormalOrder abnormalOrder = buildAbnormalOrder(
                normalizedOrderNo,
                eventId,
                eventType,
                normalizedSessionId,
                requestId,
                provider,
                domainType,
                reason,
                errorLine,
                abnormalType,
                initialStatus,
                now
        );
        try {
            abnormalOrderRepository.save(abnormalOrder);
        } catch (DataIntegrityViolationException ex) {
            Optional<AbnormalOrder> concurrentExisting = lockExistingAbnormal(normalizedSessionId);
            if (concurrentExisting.isPresent()) {
                AbnormalOrder existingAbnormal = concurrentExisting.get();
                if (isTerminalStatus(existingAbnormal.getStatus())) {
                    return;
                }
                mergeDuplicateAbnormal(
                        existingAbnormal,
                        normalizedOrderNo,
                        eventId,
                        eventType,
                        normalizedSessionId,
                        requestId,
                        provider,
                        domainType,
                        reason,
                        errorLine,
                        abnormalType,
                        now
                );
                abnormalOrderRepository.save(existingAbnormal);
                return;
            }
            throw ex;
        }
    }

    private String resolveAbnormalOrderNo(String orderNo) {
        if (!hasText(orderNo)) {
            return null;
        }
        return orderNo;
    }

    private String resolveAbnormalSessionId(String sessionId) {
        if (!hasText(sessionId)) {
            return null;
        }
        return sessionId;
    }

    public void retryAbnormalOrder() {
        LocalDateTime now = LocalDateTime.now();
        List<AbnormalOrder> retryCandidates = abnormalOrderRepository.findRetryCandidates(
                now,
                now.minus(RECONCILING_LEASE_TIMEOUT),
                PageRequest.of(0, 100)
        );

        for (AbnormalOrder retryCandidate : retryCandidates) {
            String trackingKey = resolveRetryLockKey(retryCandidate);
            RLock lock = redissonClient.getLock("AbnormalOrder:lock:" + trackingKey);
            try {
                boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
                if (!locked) {
                    log.warn("Failed to acquire lock for abnormal tracking {}, another thread is processing", trackingKey);
                    continue;
                }
                dispatchByDomain(retryCandidate);
            } catch (PaymentChannelException e) {
                markRetryFailure(retryCandidate, "provider_retrieve_failed: " + e.getMessage());
            } catch (Exception e) {
                markRetryFailure(retryCandidate, "reconcile_failed: " + e.getMessage());
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    private String resolveRetryLockKey(AbnormalOrder abnormalOrder) {
        if (abnormalOrder.getOrderNo() != null && !abnormalOrder.getOrderNo().isBlank()) {
            return abnormalOrder.getOrderNo();
        }
        if (abnormalOrder.getSessionId() != null && !abnormalOrder.getSessionId().isBlank()) {
            return abnormalOrder.getSessionId();
        }
        if (abnormalOrder.getEventId() != null && !abnormalOrder.getEventId().isBlank()) {
            return abnormalOrder.getEventId();
        }
        return String.valueOf(abnormalOrder.getId());
    }

    private void dispatchByDomain(AbnormalOrder retryCandidate) throws PaymentChannelException {
        AbnormalDomainType domainType = retryCandidate.getDomainType();
        if (domainType == null) {
            domainType = resolveDomainTypeByEventType(retryCandidate.getEventType());
        }
        if (domainType == null) {
            domainType = AbnormalDomainType.SYSTEM;
        }

        switch (domainType) {
            case ORDER -> orderAbnormalService.reconcile(retryCandidate);
            case SUBSCRIPTION -> subscriptionAbnormalService.reconcileSubscription(retryCandidate);
            case INVOICE -> subscriptionAbnormalService.reconcileInvoice(retryCandidate);
            case SYSTEM -> subscriptionAbnormalService.markManualReview(retryCandidate, "system_domain_abnormal");
        }
    }

    private void markRetryFailure(AbnormalOrder retryCandidate, String message) {
        int nextRetryCount = retryCandidate.getRetryCount() + 1;
        LocalDateTime now = LocalDateTime.now();
        retryCandidate.setRetryCount(nextRetryCount);
        retryCandidate.setLastRetryAt(now);
        retryCandidate.setUpdatedAt(now);
        retryCandidate.setErrorMessage(message);
        retryCandidate.setNextRetryAt(now.plus(processingStale.multipliedBy(nextRetryCount)));
        if (nextRetryCount >= MAX_RETRY_COUNT) {
            retryCandidate.setStatus(AbnormalOrderStatus.EXHAUSTED);
        } else {
            retryCandidate.setStatus(AbnormalOrderStatus.PENDING);
        }
        abnormalOrderRepository.save(retryCandidate);
    }

    private AbnormalOrderStatus resolveInitialStatus(AbnormalOrderType abnormalType) {
        if (abnormalType == AbnormalOrderType.PROVIDER_TERMINAL_FAILURE
                || abnormalType == AbnormalOrderType.PRE_BUSINESS_STUCK) {
            return AbnormalOrderStatus.MANUAL_REVIEW;
        }
        return AbnormalOrderStatus.PENDING;
    }

    private Optional<AbnormalOrder> lockExistingAbnormal(String sessionId) {
        if (!hasText(sessionId)) {
            return Optional.empty();
        }
        return abnormalOrderRepository.findBySessionIdForUpdate(sessionId);
    }

    private boolean isTerminalStatus(AbnormalOrderStatus status) {
        return status == AbnormalOrderStatus.FIXED
                || status == AbnormalOrderStatus.MANUAL_REVIEW
                || status == AbnormalOrderStatus.EXHAUSTED
                || status == AbnormalOrderStatus.UNPAID_CONFIRMED
                || status == AbnormalOrderStatus.RETRY_EXHAUSTED;
    }

    private AbnormalOrder buildAbnormalOrder(
            String orderNo,
            String eventId,
            BusinessEventType eventType,
            String sessionId,
            String requestId,
            String provider,
            AbnormalDomainType domainType,
            String reason,
            String errorMessage,
            AbnormalOrderType abnormalType,
            AbnormalOrderStatus initialStatus,
            LocalDateTime now
    ) {
        return AbnormalOrder.builder()
                .orderNo(orderNo)
                .eventId(eventId)
                .eventType(eventType == null ? null : eventType.name())
                .sessionId(sessionId)
                .requestId(requestId)
                .provider(provider)
                .domainType(domainType)
                .reason(reason)
                .errorMessage(errorMessage)
                .retryCount(1)
                .abnormalType(abnormalType)
                .status(initialStatus)
                .createdAt(now)
                .updatedAt(now)
                .lastRetryAt(now)
                .nextRetryAt(now)
                .build();
    }

    private void mergeDuplicateAbnormal(
            AbnormalOrder abnormalOrder,
            String orderNo,
            String eventId,
            BusinessEventType eventType,
            String sessionId,
            String requestId,
            String provider,
            AbnormalDomainType domainType,
            String reason,
            String errorMessage,
            AbnormalOrderType abnormalType,
            LocalDateTime now
    ) {
        if (hasText(orderNo)) {
            abnormalOrder.setOrderNo(orderNo);
        }
        if (hasText(eventId)) {
            abnormalOrder.setEventId(eventId);
        }
        if (eventType != null) {
            abnormalOrder.setEventType(eventType.name());
        }
        if (hasText(sessionId)) {
            abnormalOrder.setSessionId(sessionId);
        }
        if (hasText(requestId)) {
            abnormalOrder.setRequestId(requestId);
        }
        if (hasText(provider)) {
            abnormalOrder.setProvider(provider);
        }
        if (domainType != null) {
            abnormalOrder.setDomainType(domainType);
        }
        if (hasText(reason)) {
            abnormalOrder.setReason(reason);
        }
        if (abnormalType != null && abnormalOrder.getAbnormalType() == null) {
            abnormalOrder.setAbnormalType(abnormalType);
        }
        abnormalOrder.setErrorMessage(appendErrorMessage(abnormalOrder.getErrorMessage(), errorMessage));
        abnormalOrder.setUpdatedAt(now);
        abnormalOrder.setLastRetryAt(now);
        if (abnormalOrder.getNextRetryAt() == null) {
            abnormalOrder.setNextRetryAt(now);
        }
    }

    private String appendErrorMessage(String current, String next) {
        if (!hasText(next)) {
            return current;
        }
        if (!hasText(current)) {
            return next;
        }
        return current + "\n" + next;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private AbnormalDomainType resolveDomainType(BusinessEventType eventType) {
        if (eventType == null) {
            return AbnormalDomainType.SYSTEM;
        }
        String name = eventType.name();
        if (name.startsWith("ORDER_") || name.startsWith("CHECKOUT_SESSION_")) {
            return AbnormalDomainType.ORDER;
        }
        if (name.startsWith("SUBSCRIPTION_") || name.startsWith("CUSTOMER_SUBSCRIPTION_")
                // Legacy typo compatibility for already-persisted abnormal rows.
               ) {
            return AbnormalDomainType.SUBSCRIPTION;
        }
        if (name.startsWith("INVOICE_")) {
            return AbnormalDomainType.INVOICE;
        }
        return AbnormalDomainType.SYSTEM;
    }

    private AbnormalDomainType resolveDomainTypeByEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return AbnormalDomainType.SYSTEM;
        }
        if (eventType.startsWith("ORDER_") || eventType.startsWith("CHECKOUT_SESSION_")) {
            return AbnormalDomainType.ORDER;
        }
        if (eventType.startsWith("SUBSCRIPTION_") || eventType.startsWith("CUSTOMER_SUBSCRIPTION_")
                // Legacy typo compatibility for already-persisted abnormal rows.
        ) {
            return AbnormalDomainType.SUBSCRIPTION;
        }
        if (eventType.startsWith("INVOICE_")) {
            return AbnormalDomainType.INVOICE;
        }
        return AbnormalDomainType.SYSTEM;
    }
}
