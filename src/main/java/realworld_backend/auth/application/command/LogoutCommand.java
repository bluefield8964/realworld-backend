package realworld_backend.auth.application.command;

public record LogoutCommand(String requestId, String userAgent,String remoteIp,Long userId,
                            String sessionId
) {
}
