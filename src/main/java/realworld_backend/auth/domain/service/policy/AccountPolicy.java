package realworld_backend.auth.domain.service.policy;

import realworld_backend.auth.application.command.PasswordLoginCommand;
import realworld_backend.auth.domain.enumerous.LoginIdentifier;
import realworld_backend.auth.domain.exception.UserAuthException;

public interface AccountPolicy {
    Boolean validateUsername(String username)  throws UserAuthException;
    Boolean validateEmail(String email)  throws UserAuthException;

    Boolean validatePassword(String password);
}
