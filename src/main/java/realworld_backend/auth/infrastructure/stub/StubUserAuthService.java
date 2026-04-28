package realworld_backend.auth.infrastructure.stub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import realworld_backend.auth.application.command.PasswordLoginCommand;
import realworld_backend.auth.domain.enumerous.AccountStatus;
import realworld_backend.auth.domain.enumerous.LoginIdentifier;
import realworld_backend.auth.domain.enumerous.LoginIdentifierType;
import realworld_backend.auth.domain.exception.AuthException;
import realworld_backend.auth.domain.exception.UserAuthException;
import realworld_backend.auth.domain.model.UserAuthProfile;
import realworld_backend.auth.domain.service.UserAuthReader;
import realworld_backend.auth.domain.service.policy.AccountPolicy;
import realworld_backend.auth.repository.UserAuthProfileRepository;
import realworld_backend.common.exception.ErrorCode;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StubUserAuthService implements UserAuthReader {
    private final PasswordEncoder passwordEncoder;
    private final AccountPolicy accountPolicy;

    private final UserAuthProfileRepository userAuthProfileRepository;

    @Override
    public UserAuthProfile findByUsernameOrEmail(PasswordLoginCommand command) throws AuthException {
        UserAuthProfile user;
        if (command.username() != null ) {
            user = userAuthProfileRepository.findByUsername(command.username());
        } else if (command.email() != null) {
            user = userAuthProfileRepository.findByEmail(command.email());
        } else {
            throw new UserAuthException(ErrorCode.INVALID_IDENTIFIER);
        }
        if (user == null) {
            throw new UserAuthException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Override
    public void validate(PasswordLoginCommand command) throws AuthException {

        String password = command.password();
        accountPolicy.validateUsername(command.username());
        accountPolicy.validateEmail(command.email());
        accountPolicy.validatePassword(password);


    }

    @Override
    public void checkDuplicate(PasswordLoginCommand command) {
        String username = command.username();
        String email = command.email();
        if (userAuthProfileRepository.findByUsername(username) != null
               || userAuthProfileRepository.findByEmail(email) != null) {
            throw new UserAuthException(ErrorCode.USER_ALREADY_REGISTERED);
        }
    }

    @Override
    public UserAuthProfile createUser(PasswordLoginCommand command) {
        UserAuthProfile user = UserAuthProfile.builder()
                .username(command.username())
                .email(command.email())
                .passwordHash(passwordEncoder.encode(command.password()))
                .mfaEnabled(false)
                .status(AccountStatus.ACTIVE)
                .build();
        return userAuthProfileRepository.save(user);
    }

    @Override
    public UserAuthProfile findByUserId(Long userId) {
        Optional<UserAuthProfile> byId = userAuthProfileRepository.findById(userId);

        if (byId.isPresent()) {
          return   byId.get();
        } else  {
            throw new UserAuthException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private UserAuthProfile findByIdentifier(LoginIdentifier parse) {
        LoginIdentifierType type = parse.type();
        String identifier = parse.value();
        if (type.equals(LoginIdentifierType.EMAIL)) {
            return userAuthProfileRepository.findByEmail(identifier);
        } else if (type.equals(LoginIdentifierType.USERNAME)) {
            return userAuthProfileRepository.findByUsername(identifier);
        }
        return null;
    }



    public void updatePasswordHash(Long userId, String upgradedHash) {
        int updated = userAuthProfileRepository.updatePasswordHashByUserId(userId, upgradedHash);
        if (updated == 0) {
            throw new AuthException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
