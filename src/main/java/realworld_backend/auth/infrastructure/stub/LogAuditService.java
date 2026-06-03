package realworld_backend.auth.infrastructure.stub;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.auth.domain.model.AuthAuditLog;
import realworld_backend.auth.domain.service.AuditService;
import realworld_backend.auth.repository.AuthAuditLogRepository;

import java.time.LocalDateTime;

@Slf4j
@Component
@AllArgsConstructor
public class LogAuditService implements AuditService {
    private final AuthAuditLogRepository auditLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogoutSuccess(Long userId,String ip,String deviceId,String requestId,String userAgent,String sessionId) {
        auditLogRepository.save(new AuthAuditLog
                (userId, "logout", "success", ip, deviceId,
                        requestId, userAgent, "{\"sessionId\":\"" + sessionId + "\"}",
                        UtcTimeMapper.nowUtc()));
        log.info("audit eventType=logout, userId={}, requestId={}", userId, requestId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType, Long userId,
                       String result, String userAgent,
                       String ip, String deviceId,
                       String requestId, String sessionId) {

        auditLogRepository.save(new AuthAuditLog
                (userId, eventType, result, ip, deviceId,
                        requestId, userAgent, "{\"sessionId\":\"" + sessionId + "\"}",
                        UtcTimeMapper.nowUtc()));
        log.info("audit eventType={}, userId={}, requestId={}", eventType, userId, requestId);
    }


}

