package realworld_backend.auth.infrastructure.stub;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import realworld_backend.auth.domain.model.LoginContext;
import realworld_backend.auth.domain.model.UserAuthProfile;
import realworld_backend.auth.domain.service.MfaPolicy;
import realworld_backend.auth.domain.service.RiskDecision;

@Slf4j
@Component
public class NoopMfaPolicy implements MfaPolicy {
    @Override
    public boolean requiresMfa(UserAuthProfile user
            , RiskDecision riskDecision
            , LoginContext loginContext) {
        if (user.isMfaEnabled()) {
            return riskDecision.isRequireMfa();
        }
        return false;
    }
}
