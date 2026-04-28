package realworld_backend.auth.domain.service;

public interface  AuditService {
    public void recordLogoutSuccess(Long userId,String ip,String deviceId,String requestId,String userAgent,String sessionId);
    public void record(String eventType, Long userId, String result, String userAgent ,String ip,String deviceId, String requestId,String sessionId);}
