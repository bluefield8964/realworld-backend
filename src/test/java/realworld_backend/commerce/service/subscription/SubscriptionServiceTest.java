package realworld_backend.commerce.service.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.auth.domain.model.UserAuthProfile;
import realworld_backend.auth.repository.UserAuthProfileRepository;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.enums.ProviderType;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.repository.CustomerSubscriptionRepository;
import realworld_backend.commerce.repository.SubscriptionPlanRepository;
import realworld_backend.commerce.service.core.PaymentChannelRouter;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private CustomerSubscriptionRepository customerSubscriptionRepository;
    @Mock
    private UserAuthProfileRepository userAuthProfileRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private SubscriptionHistoryService subscriptionHistoryService;
    @Mock
    private PaymentChannelRouter paymentChannelRouter;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(
                redisTemplate,
                customerSubscriptionRepository,
                userAuthProfileRepository,
                subscriptionPlanRepository,
                subscriptionHistoryService,
                paymentChannelRouter
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void createOrReuseSubscriptionCheckoutShouldRejectAlreadyActiveSubscription() throws Exception {
        CurrentAuthUser authUser = new CurrentAuthUser(1L, "sess_1");
        when(valueOperations.setIfAbsent(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        when(userAuthProfileRepository.findById(1L))
                .thenReturn(Optional.of(UserAuthProfile.builder().id(1L).build()));
        when(customerSubscriptionRepository.findByActiveKey("1:plan_basic"))
                .thenReturn(Optional.of(CustomerSubscription.builder()
                        .subscriptionNo("sub_no_123")
                        .provider(ProviderType.STRIPE)
                        .status(SubscriptionStatus.ACTIVE)
                        .currentPeriodEnd(LocalDateTime.now().plusDays(10))
                        .build()));

        BizException exception = assertThrows(
                BizException.class,
                () -> subscriptionService.createOrReuseSubscriptionCheckout(authUser, ProviderType.STRIPE, "plan_basic")
        );

        assertEquals(ErrorCode.SUBSCRIPTION_ALREADY_CREATED, exception.getErrorCode());
        verifyNoInteractions(subscriptionHistoryService, paymentChannelRouter, subscriptionPlanRepository);
    }
}
