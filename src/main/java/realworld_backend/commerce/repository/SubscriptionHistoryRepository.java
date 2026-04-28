package realworld_backend.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.commerce.model.subscription.SubscriptionHistory;

import java.util.Optional;

@Repository
public interface SubscriptionHistoryRepository  extends JpaRepository<SubscriptionHistory, Long> {
    Optional<SubscriptionHistory> findBySubscription_subscriptionNo(String subscriptionNo);
    Optional<SubscriptionHistory> findTopBySubscription_subscriptionNoOrderByCreatedAtDescIdDesc(String subscriptionNo);
}

