package realworld_backend.auth.domain.service;

import realworld_backend.auth.domain.model.LoginContext;

public interface RiskService {
    RiskDecision evaluateLogin(LoginContext loginContext);
}
