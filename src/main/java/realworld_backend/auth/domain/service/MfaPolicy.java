package realworld_backend.auth.domain.service;

import realworld_backend.auth.domain.model.LoginContext;
import realworld_backend.auth.domain.model.UserAuthProfile;

public interface MfaPolicy {
    boolean requiresMfa(UserAuthProfile user, RiskDecision riskDecision, LoginContext loginContext);
}
