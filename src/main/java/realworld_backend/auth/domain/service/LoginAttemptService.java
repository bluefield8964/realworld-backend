package realworld_backend.auth.domain.service;

import realworld_backend.auth.application.command.PasswordLoginCommand;

public interface LoginAttemptService {
    void onPasswordSuccess(PasswordLoginCommand command);
    void onPasswordFailure(PasswordLoginCommand command);}
