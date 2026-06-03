package realworld_backend.commerce.service.subscription;

import realworld_backend.common.time.UtcTimeMapper;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.model.PaymentStatus;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.SubscriptionHistory;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.repository.SubscriptionHistoryRepository;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SubscriptionHistoryService {
    public enum AppendOutcome {
        APPENDED,
        HISTORY_MISSING,
        LATEST_PAYMENT_STATUS_MISMATCH
    }

    public record AppendResult(AppendOutcome outcome) {
        public boolean appended() {
            return outcome == AppendOutcome.APPENDED;
        }
    }

    private final SubscriptionHistoryRepository subscriptionHistoryRepository;

    public void recordInit(CustomerSubscription subscription) {

        SubscriptionHistory generated = SubscriptionHistory.builder().subscription(subscription)
                .createdAt(UtcTimeMapper.nowUtc())
                .action(SubscriptionStatus.CREATED)
                .toStatus(SubscriptionStatus.CREATED)
                .paymentStatus(PaymentStatus.INIT)
                .reason("generating")
                .build();
        subscriptionHistoryRepository.save(generated);
    }

    public void recordFail(CustomerSubscription subscription, Exception e) {
        SubscriptionHistory fail = SubscriptionHistory.builder().subscription(subscription)
                .createdAt(UtcTimeMapper.nowUtc())
                .action(SubscriptionStatus.INITIAL_FAIL)
                .paymentStatus(PaymentStatus.FAILED)
                .fromStatus(SubscriptionStatus.CREATED)
                .toStatus(SubscriptionStatus.INITIAL_FAIL)
                .reason(e.getMessage())
                .build();
        subscriptionHistoryRepository.save(fail);

    }

    public void recordPending(CustomerSubscription subscription) {
        SubscriptionHistory success = SubscriptionHistory.builder().subscription(subscription)
                .createdAt(UtcTimeMapper.nowUtc())
                .action(SubscriptionStatus.PENDING)
                .fromStatus(SubscriptionStatus.CREATED)
                .toStatus(SubscriptionStatus.PENDING)
                .paymentStatus(PaymentStatus.PROCESSING)
                .reason("generated,waiting for customer paying")
                .build();
        subscriptionHistoryRepository.save(success);
    }

    public Optional<SubscriptionHistory> findBySubscription_subscriptionNo(String subscriptionNo) {
        return subscriptionHistoryRepository.findBySubscription_subscriptionNo(subscriptionNo);

    }

    public AppendResult appendCheckoutFailEventIfLatestPaying(String subscriptionNo) {
        return appendTransitionIfLatestPaymentStatus(
                subscriptionNo,
                SubscriptionStatus.CHECKOUT_FAIL,
                EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.PAYING),
                "subscription_checkout_fail"
        );
    }

    public AppendResult appendCheckoutExpiredEventIfLatestPaying(String subscriptionNo) {
        return appendTransitionIfLatestPaymentStatus(
                subscriptionNo,
                SubscriptionStatus.CHECKOUT_EXPIRED,
                EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.PAYING),
                "subscription_checkout_expired"
        );
    }

    public AppendResult appendCanceledEventIfLatestPaid(String subscriptionNo) {
        return appendTransitionIfLatestPaymentStatus(
                subscriptionNo,
                SubscriptionStatus.CANCELED,
                EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.PAYING, PaymentStatus.SUCCESS),
                "subscription_canceled"
        );
    }

    public AppendResult appendActiveEventIfLatestPaid(String subscriptionNo) {
        return appendTransitionIfLatestPaymentStatus(
                subscriptionNo,
                SubscriptionStatus.ACTIVE,
                EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.PAYING, PaymentStatus.SUCCESS),
                "subscription_activated"
        );
    }

    public AppendResult appendPayingEventIfLatestPending(String subscriptionNo) {
        return appendTransitionIfLatestPaymentStatus(
                subscriptionNo,
                SubscriptionStatus.PAYING,
                EnumSet.of(PaymentStatus.PROCESSING),
                "subscription_paying"
        );
    }

    private AppendResult appendTransitionIfLatestPaymentStatus(
            String subscriptionNo,
            SubscriptionStatus toStatus,
            Set<PaymentStatus> allowedLatestPaymentStatus,
            String reason
    ) {
        Optional<SubscriptionHistory> latestOptional =
                subscriptionHistoryRepository.findTopBySubscription_subscriptionNoOrderByCreatedAtDescIdDesc(subscriptionNo);
        if (latestOptional.isEmpty()) {
            return new AppendResult(AppendOutcome.HISTORY_MISSING);
        }

        SubscriptionHistory latest = latestOptional.get();
        if (allowedLatestPaymentStatus != null
                && !allowedLatestPaymentStatus.isEmpty()
                && (latest.getPaymentStatus() == null || !allowedLatestPaymentStatus.contains(latest.getPaymentStatus()))) {
            return new AppendResult(AppendOutcome.LATEST_PAYMENT_STATUS_MISMATCH);
        }

        SubscriptionStatus fromStatus = latest.getToStatus() != null ? latest.getToStatus() : latest.getAction();
        LocalDateTime now = UtcTimeMapper.nowUtc();
        SubscriptionHistory next = SubscriptionHistory.builder()
                .subscription(latest.getSubscription())
                .action(toStatus)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .paymentStatus(resolveHistoryPaymentStatus(toStatus, latest.getPaymentStatus()))
                .reason(reason)
                .effectiveDate(now)
                .createdAt(now)
                .build();
        subscriptionHistoryRepository.save(next);
        return new AppendResult(AppendOutcome.APPENDED);
    }

    private PaymentStatus resolveHistoryPaymentStatus(SubscriptionStatus toStatus, PaymentStatus fallback) {
        if (toStatus == SubscriptionStatus.ACTIVE || toStatus == SubscriptionStatus.CANCELED) {
            return PaymentStatus.SUCCESS;
        }
        if (toStatus == SubscriptionStatus.INITIAL_FAIL || toStatus == SubscriptionStatus.CHECKOUT_FAIL) {
            return PaymentStatus.FAILED;
        }
        if (toStatus == SubscriptionStatus.CHECKOUT_EXPIRED) {
            return fallback;
        }
        if (toStatus == SubscriptionStatus.PENDING) {
            return PaymentStatus.PROCESSING;
        }
        if (toStatus == SubscriptionStatus.PAYING) {
            return PaymentStatus.PAYING;
        }
        return fallback;
    }
}

