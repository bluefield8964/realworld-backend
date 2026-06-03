package realworld_backend.auth.infrastructure.stub;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import realworld_backend.auth.domain.enumerous.AccountStatus;
import realworld_backend.auth.domain.exception.UserAuthException;
import realworld_backend.auth.domain.model.UserAuthProfile;
import realworld_backend.auth.domain.service.AccountStatusChecker;
import realworld_backend.common.exception.ErrorCode;

@Slf4j
@Component
@AllArgsConstructor
public class SimpleAccountStatusChecker implements AccountStatusChecker {

    @Override
    public void checkLoginAllowed(UserAuthProfile user) throws UserAuthException {
        if (user == null) {
            throw new UserAuthException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (user.getStatus() == null) {
            throw new UserAuthException(ErrorCode.ACCOUNT_DISABLED);
        }

        if (user.getStatus() == AccountStatus.LOCKED) {
            throw new UserAuthException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (user.getStatus() == AccountStatus.DISABLED) {
            throw new UserAuthException(ErrorCode.ACCOUNT_DISABLED);
        }

        if (user.getStatus() == AccountStatus.BANNED) {
            throw new UserAuthException(ErrorCode.ACCOUNT_BANNED);
        }

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new UserAuthException(ErrorCode.ACCOUNT_DISABLED);
        }
    }
}
