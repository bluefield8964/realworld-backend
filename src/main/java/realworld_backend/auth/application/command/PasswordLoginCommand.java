package realworld_backend.auth.application.command;

import lombok.Builder;

@Builder
public record PasswordLoginCommand(
        String email,
        String username,
        String password,
        Boolean rememberMe,
        String deviceId,
        String remoteIp,
        String userAgent,
        String requestId
) {
}
