package realworld_backend.commerce.service.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.repository.CustomerSubscriptionRepository;
import realworld_backend.commerce.service.core.ProviderTimeMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class CustomerSubscriptionService {
    private final CustomerSubscriptionRepository customerSubscriptionRepository;

   public int  updateFromProviderIfStatusChanged( String subscriptionNo,
                                                  java.util.Collection<SubscriptionStatus> fromStatus,
                                                 SubscriptionStatus toStatus,
                                                  Instant providerPeriodStart,
                                                  Instant providerPeriodEnd,
                                                 Boolean cancelAtPeriodEnd,
                                                 boolean clearActiveKey,
                                                  Instant now ){
       return customerSubscriptionRepository.updateFromProviderIfStatusChanged
               (subscriptionNo,
                       fromStatus,
                       toStatus,
                       ProviderTimeMapper.toUtcLocalDateTime(providerPeriodStart),
                       ProviderTimeMapper.toUtcLocalDateTime(providerPeriodEnd),
                       cancelAtPeriodEnd,
                       clearActiveKey,
                       ProviderTimeMapper.toUtcLocalDateTime(now));

   }

    public Optional<CustomerSubscription> findBySubscriptionNo(String subscriptionNo){
        Optional<CustomerSubscription> bySubscriptionNo = customerSubscriptionRepository.findBySubscriptionNo(subscriptionNo);
    return bySubscriptionNo;
   }
    public Optional<CustomerSubscription> findByProviderSubscriptionId(String providerSubscriptionId){
        Optional<CustomerSubscription> providerSubscription = customerSubscriptionRepository.findByProviderSubscriptionId(providerSubscriptionId);
        return providerSubscription;
    }
    public Optional<CustomerSubscription> findByProviderCustomerId(String providerCustomerId){
        Optional<CustomerSubscription> providerSubscription = customerSubscriptionRepository.findByProviderCustomerId(providerCustomerId);
        return providerSubscription;
    }

    public List<CustomerSubscription> findByUserIdOrderByCurrentPeriodEndDesc(Long userId) {
        return customerSubscriptionRepository.findByUser_IdOrderByCurrentPeriodEndDesc(userId);
    }

    public CustomerSubscription save(CustomerSubscription customerSubscription) {
        return customerSubscriptionRepository.save(customerSubscription);
    }

    public int markCheckoutEventObservedIfNewer(String subscriptionNo, Instant eventCreatedAt, Instant now) {
        return customerSubscriptionRepository.markCheckoutEventObservedIfNewer(
                subscriptionNo,
                ProviderTimeMapper.toUtcLocalDateTime(eventCreatedAt),
                ProviderTimeMapper.toUtcLocalDateTime(now)
        );
    }

    public int markLifecycleEventObservedIfNewer(String subscriptionNo, Instant eventCreatedAt, Instant now) {
        return customerSubscriptionRepository.markLifecycleEventObservedIfNewer(
                subscriptionNo,
                ProviderTimeMapper.toUtcLocalDateTime(eventCreatedAt),
                ProviderTimeMapper.toUtcLocalDateTime(now)
        );
    }

}
