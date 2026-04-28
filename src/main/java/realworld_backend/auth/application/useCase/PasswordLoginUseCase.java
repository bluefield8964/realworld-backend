package realworld_backend.auth.application.useCase;

import realworld_backend.auth.application.command.LogoutCommand;
import realworld_backend.auth.application.command.PasswordLoginCommand;
import realworld_backend.auth.application.result.LoginResult;
import realworld_backend.auth.domain.model.TokenPair;


public interface PasswordLoginUseCase {
    public LoginResult login(PasswordLoginCommand command);

    public PasswordLoginCommand register(PasswordLoginCommand command);

    public void logout(LogoutCommand authUser);

    TokenPair refreshTokenRotation(String refreshToken, String requestId, String Agent, String ip) ;
}
