package realworld_backend.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CustomerSubscriptionRepository extends JpaRepository<CustomerSubscription, Long> {
    Optional<CustomerSubscription> findByActiveKey(String activeKey);

    @Modifying
    @Query(value = """
            INSERT INTO customer_subscriptions
            (subscription_no, user_id, plan_id, provider, subscription_url, status,
             provider_subscription_id, provider_customer_id, current_period_start, active_key,
             current_period_end, cancel_at_period_end, canceled_at, created_at, updated_at)
            VALUES
            (:subscriptionNo, :userId, :planId, :provider, :subscriptionUrl, :status,
             :providerSubscriptionId, :providerCustomerId, :currentPeriodStart, :activeKey,
             :currentPeriodEnd, :cancelAtPeriodEnd, :canceledAt, :createdAt, :updatedAt)
            ON DUPLICATE KEY UPDATE
                user_id = VALUES(user_id),
                plan_id = VALUES(plan_id),
                provider = VALUES(provider),
                subscription_url = VALUES(subscription_url),
                status = VALUES(status),
                provider_subscription_id = COALESCE(VALUES(provider_subscription_id), provider_subscription_id),
                provider_customer_id = COALESCE(VALUES(provider_customer_id), provider_customer_id),
                current_period_start = COALESCE(VALUES(current_period_start), current_period_start),
                active_key = VALUES(active_key),
                current_period_end = COALESCE(VALUES(current_period_end), current_period_end),
                cancel_at_period_end = COALESCE(VALUES(cancel_at_period_end), cancel_at_period_end),
                canceled_at = VALUES(canceled_at),
                updated_at = VALUES(updated_at)
            """, nativeQuery = true)
    int upsertBySubscriptionNo(
            @Param("subscriptionNo") String subscriptionNo,
            @Param("userId") Long userId,
            @Param("planId") Long planId,
            @Param("provider") String provider,
            @Param("subscriptionUrl") String subscriptionUrl,
            @Param("status") String status,
            @Param("providerSubscriptionId") String providerSubscriptionId,
            @Param("providerCustomerId") String providerCustomerId,
            @Param("currentPeriodStart") LocalDateTime currentPeriodStart,
            @Param("activeKey") String activeKey,
            @Param("currentPeriodEnd") LocalDateTime currentPeriodEnd,
            @Param("cancelAtPeriodEnd") Boolean cancelAtPeriodEnd,
            @Param("canceledAt") LocalDateTime canceledAt,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying
    @Query("""
                update CustomerSubscription s
                set s.status = :toStatus,
                    s.currentPeriodStart = COALESCE(:periodStart, s.currentPeriodStart),
                    s.currentPeriodEnd = COALESCE(:periodEnd, s.currentPeriodEnd),
                    s.cancelAtPeriodEnd = COALESCE(:cancelAtPeriodEnd, s.cancelAtPeriodEnd),
                    s.updatedAt = :now
                where s.subscriptionNo = :subscriptionNo
                  and s.status = :fromStatus
            """)
    int updateFromProviderIfStatusChanged(
            @Param("subscriptionNo") String subscriptionNo,
            @Param("fromStatus") SubscriptionStatus fromStatus,
            @Param("toStatus") SubscriptionStatus toStatus,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd,
            @Param("cancelAtPeriodEnd") Boolean cancelAtPeriodEnd,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
                update CustomerSubscription s
                set s.status = :toStatus,
                    s.updatedAt = :now
                where s.subscriptionNo = :subscriptionNo
                  and s.status in :fromStatuses
            """)
    int updateStatusToPastDue(
            @Param("subscriptionNo") String subscriptionNo,
            @Param("toStatus") SubscriptionStatus toStatus,
            @Param("fromStatuses") java.util.Collection<SubscriptionStatus> fromStatuses,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
                update CustomerSubscription s
                set s.status = :toStatus,
                    s.updatedAt = :now
                where s.subscriptionNo = :subscriptionNo
                  and s.status in :fromStatuses
            """)
    int updateStatusToActiveFromRecoverable(
            @Param("subscriptionNo") String subscriptionNo,
            @Param("toStatus") SubscriptionStatus toStatus,
            @Param("fromStatuses") java.util.Collection<SubscriptionStatus> fromStatuses,
            @Param("now") LocalDateTime now
    );

    Optional<CustomerSubscription> findBySubscriptionNo(String subscriptionNo);

    Optional<CustomerSubscription> findByProviderSubscriptionId(String subscriptionId);

    Optional<CustomerSubscription> findByProviderCustomerId(String customer);
}

