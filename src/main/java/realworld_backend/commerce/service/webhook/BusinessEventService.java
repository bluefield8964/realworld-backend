package realworld_backend.commerce.service.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.BusinessEvent;
import realworld_backend.commerce.model.EventStatus;
import realworld_backend.commerce.repository.BusinessEventRepository;
import realworld_backend.commerce.service.core.ProviderTimeMapper;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Persists webhook event lifecycle rows for idempotency and retry control.
 */
@Service
@RequiredArgsConstructor
public class BusinessEventService {
    private final BusinessEventRepository businessEventRepository;

    /**
     * Reserve event slot in PROCESSING.
     * Duplicate key means the event was seen before.
     */
    @Transactional
    public void saveProcessing(String eventId, BusinessEventType eventType) {

        businessEventRepository.insertProcessing(eventId, eventType, EventStatus.PROCESSING, null, utcNow());
    }


    /**
     * Mark event success in current transaction.
     */
    @Transactional
    public void markSuccessAndIncrementAttempt(String eventId, EventStatus status, String lastError) {
        businessEventRepository.updateStatusAndIncAttempt(eventId, status, lastError, utcNow());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BusinessEvent findByIdOrThrow(String eventId) {
        return businessEventRepository.findByEventId(eventId).orElseThrow();
    }


    /**
     * Persist failure/dead status in REQUIRES_NEW so trace survives outer rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEventStatusWithAttempt(
            String eventId,
            BusinessEventType type,
            EventStatus status,
            String lastError,
            int attempt,
            int attemptInc
    ) {
        // Upsert handles both "row exists" and "row missing after conflict" cases.
        businessEventRepository.upsertFailStatus(
                eventId,
                type,
                status.name(),
                lastError,
                utcNow(),
                attempt,
                attemptInc
        );
    }

    /**
     * Persist failure/dead status in REQUIRES_NEW so trace survives outer rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markEventStatusFromFailToProcessing(
            String eventId,
            BusinessEventType type,
            EventStatus status,
            int attempt
    ) {
        // Upsert handles both "row exists" and "row missing after conflict" cases.
        return businessEventRepository.markEventStatusFromFailToProcessing(
                eventId,
                type,
                status.name(),
                utcNow(),
                attempt
        );
    }

    /**
     * Persist failure/dead status in REQUIRES_NEW so trace survives outer rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEventStatusToDeadWithAttempt(
            String eventId,
            BusinessEventType type,
            EventStatus status,
            String lastError,
            int attempt,
            int attemptInc
    ) {
        // Upsert handles both "row exists" and "row missing after conflict" cases.
        businessEventRepository.upsertDeadStatus(
                eventId,
                type,
                status.name(),
                lastError,
                utcNow(),
                attempt,
                attemptInc
        );
    }

    private LocalDateTime utcNow() {
        return ProviderTimeMapper.toUtcLocalDateTime(Instant.now());
    }
}
