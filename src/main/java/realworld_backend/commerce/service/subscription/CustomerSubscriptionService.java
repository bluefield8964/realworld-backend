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
                                                   SubscriptionStatus fromStatus,
                                                 SubscriptionStatus toStatus,
                                                  Instant providerPeriodStart,
                                                  Instant providerPeriodEnd,
                                                 Boolean cancelAtPeriodEnd,
                                                  Instant now ){
       return customerSubscriptionRepository.updateFromProviderIfStatusChanged
               (subscriptionNo,
                       fromStatus,
                       toStatus,
                       ProviderTimeMapper.toUtcLocalDateTime(providerPeriodStart),
                       ProviderTimeMapper.toUtcLocalDateTime(providerPeriodEnd),
                       cancelAtPeriodEnd,
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

    public CustomerSubscription save(CustomerSubscription customerSubscription) {
        return customerSubscriptionRepository.save(customerSubscription);
    }

    public int updateStatusToPastDue(String subscriptionNo, Instant now) {
        LocalDateTime nowUtc = ProviderTimeMapper.toUtcLocalDateTime(now);
        return customerSubscriptionRepository.updateStatusToPastDue(
                subscriptionNo,
                SubscriptionStatus.PAST_DUE,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING),
                nowUtc
        );
    }

    public int updateStatusToActiveFromRecoverable(String subscriptionNo, Instant now) {
        LocalDateTime nowUtc = ProviderTimeMapper.toUtcLocalDateTime(now);
        return customerSubscriptionRepository.updateStatusToActiveFromRecoverable(
                subscriptionNo,
                SubscriptionStatus.ACTIVE,
                List.of(SubscriptionStatus.PENDING, SubscriptionStatus.INITIAL_FAIL, SubscriptionStatus.PAST_DUE),
                nowUtc
        );
    }

}

