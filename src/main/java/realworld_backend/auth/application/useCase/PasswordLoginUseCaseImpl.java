package realworld_backend.auth.application.useCase;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.auth.application.command.LogoutCommand;
import realworld_backend.auth.application.command.PasswordLoginCommand;
import realworld_backend.auth.application.result.LoginResult;
import realworld_backend.auth.domain.exception.UserAuthException;
import realworld_backend.auth.domain.model.*;
import realworld_backend.auth.domain.service.*;

@RequiredArgsConstructor
@Service
public class PasswordLoginUseCaseImpl implements PasswordLoginUseCase {
    private final UserAuthReader userAuthReader;
    private final AccountStatusChecker accountStatusChecker;
    private final PasswordVerifier passwordVerifier;
    private final LoginAttemptService loginAttemptService;
    private final RiskService riskService;
    private final MfaPolicy mfaPolicy;
    private final SessionService sessionService;
    private final TokenService tokenService;
    private final AuditService auditService;
    private final AuthRefreshTokenService authRefreshTokenService;

    @Override
    public LoginResult login(PasswordLoginCommand command) {
        try {
            //check the encounter user info
            UserAuthProfile user = userAuthReader.findByUsernameOrEmail(command);
            //check the encounter user status
            accountStatusChecker.checkLoginAllowed(user);
            //verify the password
            passwordVerifier.verify(user, command.password());
            //encounter process after login
            loginAttemptService.onPasswordSuccess(command);
            //gain the context for flowing request
            LoginContext loginContext = LoginContext.of(
                    user.getId(),
                    command.deviceId(),
                    command.remoteIp(),
                    command.userAgent(),
                    command.requestId()
            );
            //base on the logincontext to verify the legality
            RiskDecision riskDecision = riskService.evaluateLogin(loginContext);

            //base on riskDecision,logincontext,user info to load MFA
            if (mfaPolicy.requiresMfa(user, riskDecision, loginContext)) {
                String challengeId = "TODO_MFA_CHALLENGE" + user.getId();
                auditService.record("MFA required",
                        user.getId(), "second verification",
                        command.userAgent(), command.remoteIp(),
                        command.deviceId(), command.requestId(),
                        null);
                return LoginResult.mfaRequired(challengeId);
            }
            //create individual session for convenience of customer's next requirement
            AuthSession session = sessionService.createSession(user, loginContext, riskDecision);
            //generate access token and refresh token
            TokenPair tokenPair = tokenService.issueTokenPair(user, session, command.rememberMe());
            //record into diary
            auditService.record("successful login",
                    user.getId(), "success",
                    command.userAgent(), command.remoteIp(),
                    command.deviceId(), command.requestId(),
                    session.getSessionId());
            //return token and session info
            return LoginResult.success(tokenPair, session.getSessionId());
        } catch (UserAuthException e) {
            loginAttemptService.onPasswordFailure(command);
            // according to UserAuthException code infer what log message should write down
            auditService.record("authentication error",
                    null, "failure",
                    command.userAgent(), command.remoteIp(),
                    command.deviceId(), command.requestId(),
                    null);
            throw e;
        }
    }

    @Override
    public PasswordLoginCommand register(PasswordLoginCommand command) {
        // 1. verification
        userAuthReader.validate(command);

        // 2. check duplication
        userAuthReader.checkDuplicate(command);

        // create account
        UserAuthProfile user = userAuthReader.createUser(command);


        return new PasswordLoginCommand(user.getEmail(), user.getUsername(), command.password(), command.rememberMe(), command.deviceId(), command.remoteIp(), command.userAgent(), command.requestId());

    }

    @Override
    @Transactional
    public void logout(LogoutCommand command) {
        try {
            AuthSession session = sessionService.revokeBySessionId(command.userId(), command.sessionId());
            if (session == null) {
                return;
            }

            authRefreshTokenService.revokeBySessionId(session.getSessionId());

            auditService.recordLogoutSuccess(
                    command.userId(),
                    command.remoteIp(),
                    session.getDeviceId(),
                    command.requestId(),
                    command.userAgent(),
                    command.sessionId()
            );
        } catch (UserAuthException e) {
            auditService.record("logout error",
                    command.userId(), "failure",
                    command.userAgent(), command.remoteIp(),
                    null, command.requestId(),
                    command.sessionId());
            throw e;
        }
    }

    @Override
    public TokenPair refreshTokenRotation(String refreshToken, String requestId, String Agent, String ip) {
        try {
            //find match refreshToken
            AuthRefreshToken oldRefreshToken = authRefreshTokenService.findByRefreshToken(refreshToken);
            //find match authSession
            String sessionId = oldRefreshToken.getSessionId();
            AuthSession authSession  =sessionService.findBySessionId(sessionId);
            //find match user
            Long userId = authSession.getUserId();
            UserAuthProfile user = userAuthReader.findByUserId(userId);
            //destroy elderRefreshToken then generate lasted one
            String newRefreshToken = authRefreshTokenService.rotationRefreshToken(oldRefreshToken);

            //turn accessToken and refreshToken
            return tokenService.issueRefreshedTokenPair(authSession.getSessionId(),newRefreshToken, user);
        } catch (UserAuthException ex) {
            auditService.record("rotation of refreshToken fail",
                    null, "failure",
                    Agent, ip,
                    null, requestId,
                    null);
            throw ex;
        }
    }
}
