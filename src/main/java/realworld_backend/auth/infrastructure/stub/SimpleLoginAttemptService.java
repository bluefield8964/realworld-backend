package realworld_backend.auth.infrastructure.stub;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import realworld_backend.auth.application.command.PasswordLoginCommand;
import realworld_backend.auth.domain.exception.UserAuthException;
import realworld_backend.auth.domain.service.LoginAttemptService;
import realworld_backend.common.exception.ErrorCode;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@AllArgsConstructor
public class SimpleLoginAttemptService implements LoginAttemptService {
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onPasswordSuccess(PasswordLoginCommand command) {
        Long count;
        String key;
        if (command.username() != null) {
            key = "auth:login:fail:" + command.username();
            redisTemplate.delete(key);
            log.info("login success reset fail count, username={}", command.username());
        } else if (command.email() != null) {
            key = "auth:login:fail:" + command.email();
            redisTemplate.delete(key);
            log.info("login success reset fail count, email={}", command.email());
        } else {
            throw new UserAuthException(ErrorCode.INVALID_IDENTIFIER);
        }
    }

    @Override
    public void onPasswordFailure(PasswordLoginCommand command) {
        Long count;
        String key;
        if (command.username() != null) {
            key = "auth:login:fail:" + command.username();
            count = redisTemplate.opsForValue().increment(key);

        } else if (command.email() != null) {
            key = "auth:login:fail:" + command.email();
            count = redisTemplate.opsForValue().increment(key);
        } else {
            throw new UserAuthException(ErrorCode.INVALID_IDENTIFIER);
        }
        // setting the expired time at first time
        if (count != null && count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.DAYS);
        }
        log.warn("login failure, username={}, ip={}, count={}", command.username() + "" + command.remoteIp(), count);
    }
}
