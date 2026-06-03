package realworld_backend.auth.infrastructure.stub;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import realworld_backend.auth.domain.exception.UserAuthException;
import realworld_backend.auth.domain.model.UserAuthProfile;
import realworld_backend.auth.domain.service.PasswordVerifier;
import realworld_backend.common.exception.ErrorCode;

@Slf4j
@Component
@AllArgsConstructor
public class SpringPasswordVerifier implements PasswordVerifier {
    private final PasswordEncoder passwordEncoder;
    private final StubUserAuthService stubUserAuthService;

    @Override
    public void verify(UserAuthProfile user, String rawPassword) {
        if ( user.getPasswordHash() == null || rawPassword == null) {
            throw new UserAuthException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UserAuthException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            String upgradedHash = passwordEncoder.encode(rawPassword);
            stubUserAuthService.updatePasswordHash(user.getId(), upgradedHash);
        }
    }
}
