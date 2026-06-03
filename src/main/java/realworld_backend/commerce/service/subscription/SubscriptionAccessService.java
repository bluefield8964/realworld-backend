package realworld_backend.commerce.service.subscription;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.commerce.service.entitlement.EntitlementFacade;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SubscriptionAccessService {
    private final EntitlementFacade entitlementFacade;

    public void requireActiveSubscription(CurrentAuthUser currentUser) {
        if (currentUser == null || currentUser.userId() == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        if (!hasActiveSubscription(currentUser.userId(), UtcTimeMapper.nowUtc())) {
            throw new BizException(ErrorCode.SUBSCRIPTION_REQUIRED);
        }
    }

    public boolean hasActiveSubscription(Long userId, LocalDateTime now) {
        if (userId == null) {
            return false;
        }
        return entitlementFacade.hasAnyBundleAccess(userId, now);
    }
}

