package realworld_backend.commerce.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.BusinessEvent;
import realworld_backend.commerce.model.EventStatus;
import realworld_backend.commerce.service.core.ProviderTimeMapper;

import realworld_backend.commerce.repository.BusinessEventRepository;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
/**
 * Persists webhook event lifecycle for idempotency and retry control.
 */
public class BusinessEventService {
    private final BusinessEventRepository businessEventRepository;

    /**
     * Reserve event slot in PROCESSING.
     * Duplicate key means the event was seen before.
     */
    public void saveProcessing(String eventId, BusinessEventType eventType) {
        businessEventRepository.save(
                new BusinessEvent(eventId, eventType, EventStatus.PROCESSING, null, utcNow(), 0)
        );
    }

    public BusinessEvent findByIdForUpdateOrThrow(String eventId) {
        return businessEventRepository.findByIdForUpdate(eventId).orElseThrow();
    }

    /**
     * Mark event success in current transaction.
     */
    public void markSuccessAndIncrementAttempt(String eventId, EventStatus status, String lastError) {
        businessEventRepository.updateStatusAndIncAttempt(eventId, status, lastError, utcNow());
    }

    /**
     * Persist failure/dead status in REQUIRES_NEW so trace survives outer rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEventStatusWithAttempt(String eventId,
                                                 BusinessEventType type,
                                                 EventStatus status,
                                                 String lastError,
                                                 int attempt,
                                                 int attemptInc) {
        // Upsert handles both "row exists" and "row missing after conflict" cases.
        businessEventRepository.upsertFailStatus(eventId, type, status.name(),
                lastError, utcNow(), attempt, attemptInc);
    }

    private LocalDateTime utcNow() {
        return ProviderTimeMapper.toUtcLocalDateTime(Instant.now());
    }
}


