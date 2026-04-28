package realworld_backend.auth.domain.service;

import realworld_backend.auth.application.command.PasswordLoginCommand;
import realworld_backend.auth.domain.model.UserAuthProfile;

public interface UserAuthReader {
    UserAuthProfile findByUsernameOrEmail(PasswordLoginCommand command);

    void validate(PasswordLoginCommand command);

    void checkDuplicate(PasswordLoginCommand command);

    UserAuthProfile createUser(PasswordLoginCommand command);

    UserAuthProfile findByUserId(Long userId);
}
