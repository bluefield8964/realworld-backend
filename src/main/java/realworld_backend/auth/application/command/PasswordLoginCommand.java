package realworld_backend.auth.application.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
public class PasswordLoginCommand {

    private final String username;
    private final String password;
    private final String deviceId;
    private final Boolean rememberMe;
    private final String remoteIp;
    private final String userAgent;
    private final String requestId;
}
