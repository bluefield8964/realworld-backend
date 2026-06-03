package realworld_backend.auth.infrastructure.stub;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import realworld_backend.auth.domain.enumerous.SessionStatus;
import realworld_backend.auth.domain.model.AuthSession;
import realworld_backend.auth.domain.model.LoginContext;
import realworld_backend.auth.domain.service.RiskDecision;
import realworld_backend.auth.domain.service.RiskService;
import realworld_backend.auth.domain.service.SessionService;
import java.util.Objects;

@Slf4j
@Component
@AllArgsConstructor
public class NoopRiskService implements RiskService {
    private final SessionService sessionService;

    @Override
    public RiskDecision evaluateLogin(LoginContext loginContext) {
        int score = 0;
        Long userId = loginContext.getUserId();
        String deviceId = loginContext.getDeviceId();
        AuthSession authSession = sessionService.findByUserIdAndDeviceId(userId, deviceId);
        //first time login in this way ,check if user require mfa
        if (authSession == null) {
            return new RiskDecision("RiskLevel.LOW", true);
        }
        //ip blacklist to prevent brute force cracking
        ////////////////////////////////////////////////////
        //////////////////////////////////////////////////
        ////////////////////////////////////////////////


        if (authSession.getStatus() == SessionStatus.REVOKED || authSession.getStatus() == SessionStatus.EXPIRED) {
            score -= 20;
        }

        //use the last time ip compare to this times
        if (Objects.equals(authSession.getIp(), loginContext.getIp())) {
            //extra point
            score += 30;
        }
        if (Objects.equals(authSession.getDeviceId(), loginContext.getDeviceId())) {
            //extra point
            score += 30;
        }
        if (Objects.equals(authSession.getUserAgent(), loginContext.getUserAgent())) {
            //extra point
            score += 30;
        }
        if (score >= 60) {
            return RiskDecision.allow();
        } else if (score >= 30) {
            return new RiskDecision("RiskLevel.MEDIUM", true);
        } else {
            return new RiskDecision("RiskLevel.LOW", true);
        }

    }
}
