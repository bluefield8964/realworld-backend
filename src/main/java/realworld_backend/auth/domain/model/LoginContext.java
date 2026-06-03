package realworld_backend.auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginContext {
    private final Long userId;
    private final String deviceId;
    private final String ip;
    private final String userAgent;
    private final String requestId;
    public static LoginContext of(Long userId, String deviceId, String ip, String userAgent, String requestId) {
        return new LoginContext(userId, deviceId, ip, userAgent, requestId);
    }
}
