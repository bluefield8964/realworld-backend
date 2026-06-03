package realworld_backend.commerce.service.subscription;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.auth.domain.model.UserAuthProfile;
import realworld_backend.auth.repository.UserAuthProfileRepository;
import realworld_backend.commerce.model.core.CheckoutSessionData;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.SubscriptionPlan;
import realworld_backend.commerce.model.subscription.enums.ProviderType;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.repository.CustomerSubscriptionRepository;
import realworld_backend.commerce.repository.SubscriptionPlanRepository;
import realworld_backend.commerce.service.core.PaymentChannel;
import realworld_backend.commerce.service.core.PaymentChannelException;
import realworld_backend.commerce.service.core.PaymentChannelRouter;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final CustomerSubscriptionRepository customerSubscriptionRepository;
    private final UserAuthProfileRepository userAuthProfileRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionHistoryService subscriptionHistoryService;
    private final PaymentChannelRouter paymentChannelRouter;


    /**
     * Main subscription-entry flow from API.
     */

    @Transactional
    public String createOrReuseSubscriptionCheckout(CurrentAuthUser currentUser, ProviderType provider, String planCode) throws Exception {
        Long userId = currentUser.userId();
        String activeKey = buildActiveKey(userId, planCode);
        String lockKey = "pay:lock:" + activeKey;
        CustomerSubscription customerSubscription = null;
        CheckoutSessionData session = null;
        try {
            // Short lock to avoid double-click / rapid re-submit.
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
            if (!success) {
                throw new BizException(ErrorCode.SUBSCRIPTION_ALREADY_CREATED);
            }
            // Reuse active pending order; otherwise create a new one.
            customerSubscription = createSubscription(userId, provider, planCode, activeKey);
            if (customerSubscription.isActiveNow(UtcTimeMapper.nowUtc())) {
                throw new BizException(ErrorCode.SUBSCRIPTION_ALREADY_CREATED);
            }
            // Keep same checkout URL for active pending order.
            if (customerSubscription.getStatus() == SubscriptionStatus.PENDING) {
                return customerSubscription.getSubscriptionUrl();
            }
            if (customerSubscription.getStatus() == SubscriptionStatus.PAYING) {
                throw new BizException(ErrorCode.SUBSCRIPTION_ALREADY_CREATED);
            }

            subscriptionHistoryService.recordInit(customerSubscription);
            try {
                session = createSubscriptionSession(customerSubscription);
            } catch (PaymentChannelException e) {
                subscriptionHistoryService.recordFail(customerSubscription, e);
                saveFailSubscription(customerSubscription);
                throw e;
            }
            String url = savePendingSubscription(customerSubscription, session);
            subscriptionHistoryService.recordPending(customerSubscription);
            return url;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private String savePendingSubscription(CustomerSubscription customerSubscription, CheckoutSessionData session) {
        String url = session.getUrl();
        LocalDateTime now = UtcTimeMapper.nowUtc();
        customerSubscription.setStatus(SubscriptionStatus.PENDING);
        customerSubscription.setSubscriptionUrl(url);
        customerSubscription.setUpdatedAt(now);
        upsertSubscriptionBySubscriptionNo(customerSubscription);
        return url;
    }

    private CheckoutSessionData createSubscriptionSession(CustomerSubscription customerSubscription) {
        PaymentChannel paymentChannel = paymentChannelRouter.get(String.valueOf(customerSubscription.getProvider()));
        return paymentChannel.createSubscriptionSession(customerSubscription);
    }

    public void saveFailSubscription(CustomerSubscription customerSubscription) {
        customerSubscription.setStatus(SubscriptionStatus.INITIAL_FAIL);
        customerSubscription.setActiveKey(null);
        customerSubscription.setUpdatedAt(UtcTimeMapper.nowUtc());
        customerSubscriptionRepository.save(customerSubscription);
    }


    public String buildActiveKey(Long userId, String planCode) {
        // User+product active-subscription key.
        return userId + ":" + planCode;
    }


    private CustomerSubscription createSubscription(Long userId, ProviderType provider, String planCode, String activeKey) {
        Optional<UserAuthProfile> userAuthProfileMapper = userAuthProfileRepository.findById(userId);
        if (userAuthProfileMapper.isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserAuthProfile userAuthProfile = userAuthProfileMapper.get();
        //every
        String subscriptionNo = UUID.randomUUID().toString();
        // Business-level idempotency: one active order per user+planCode key.
        Optional<CustomerSubscription> existingCustomerSubscription = customerSubscriptionRepository.findByActiveKey(activeKey);
        if (existingCustomerSubscription.isPresent()
                && (existingCustomerSubscription.get().getStatus() == SubscriptionStatus.PENDING
                || existingCustomerSubscription.get().getStatus() == SubscriptionStatus.PAYING)) {
            return existingCustomerSubscription.get();
        }
        if (existingCustomerSubscription.isPresent()
                && existingCustomerSubscription.get().isActiveNow(UtcTimeMapper.nowUtc())) {
            return existingCustomerSubscription.get();
        }
        // DB unique key is the last race guard across concurrent workers.
        Optional<SubscriptionPlan> subscriptionPlanMapper = subscriptionPlanRepository.findByplanCode(planCode);
        if (subscriptionPlanMapper.isEmpty()) {
            throw new BizException(ErrorCode.SUBSCRIPTION_PLAN_NOT_FOUND);
        }
        SubscriptionPlan subscriptionPlan = subscriptionPlanMapper.get();
        LocalDateTime now = UtcTimeMapper.nowUtc();
        CustomerSubscription customerSubscription = CustomerSubscription.builder().createdAt(now)
                .activeKey(activeKey)
                .subscriptionNo(subscriptionNo)
                .provider(provider)
                .currentPeriodStart(now)
                .currentPeriodEnd(now.plus(subscriptionPlan.getDuration()))
                .cancelAtPeriodEnd(false)
                .status(SubscriptionStatus.CREATED)
                .updatedAt(now)
                .user(userAuthProfile)
                .plan(subscriptionPlan)
                .build();

        try {
            upsertSubscriptionBySubscriptionNo(customerSubscription);
            return customerSubscriptionRepository.findBySubscriptionNo(subscriptionNo)
                    .orElseThrow(() -> new BizException(ErrorCode.CUSTOMER_SUBSCRIPTION_NOT_FOUND));
        } catch (DataIntegrityViolationException e) {
            return customerSubscriptionRepository.findByActiveKey(activeKey)
                    .orElseThrow(() -> e);
        }
    }

    private void upsertSubscriptionBySubscriptionNo(CustomerSubscription customerSubscription) {
        customerSubscriptionRepository.upsertBySubscriptionNo(
                customerSubscription.getSubscriptionNo(),
                customerSubscription.getUser().getId(),
                customerSubscription.getPlan().getId(),
                customerSubscription.getProvider().name(),
                customerSubscription.getSubscriptionUrl(),
                customerSubscription.getStatus().name(),
                customerSubscription.getProviderSubscriptionId(),
                customerSubscription.getProviderCustomerId(),
                customerSubscription.getCurrentPeriodStart(),
                customerSubscription.getActiveKey(),
                customerSubscription.getCurrentPeriodEnd(),
                customerSubscription.getCancelAtPeriodEnd(),
                customerSubscription.getCanceledAt(),
                customerSubscription.getLastCheckoutEventCreatedAt(),
                customerSubscription.getLastLifecycleEventCreatedAt(),
                customerSubscription.getCreatedAt(),
                customerSubscription.getUpdatedAt()
        );
    }

}




