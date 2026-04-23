package realworld_backend.commerce.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.commerce.model.StripeEvent;
import realworld_backend.commerce.model.StripeEventStatus;
import realworld_backend.commerce.repository.StripeEventRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
/**
 * Persists webhook event lifecycle for idempotency and retry control.
 */
public class StripeEventService {
    private final StripeEventRepository stripeEventRepository;

    /**
     * Reserve event slot in PROCESSING.
     * Duplicate key means the event was seen before.
     */
    public void saveProcessing(String eventId, String eventType) {
        stripeEventRepository.save(
                new StripeEvent(eventId, eventType, StripeEventStatus.PROCESSING, null, LocalDateTime.now(), 1)
        );
    }

    public StripeEvent findByIdForUpdateOrThrow(String eventId) {
        return stripeEventRepository.findByIdForUpdate(eventId).orElseThrow();
    }

    /**
     * Mark event success in current transaction.
     */
    public void markSuccessStatusAndIncAttempt(String eventId, StripeEventStatus status, String lastError) {
        stripeEventRepository.updateStatusAndIncAttempt(eventId, status, lastError, LocalDateTime.now());
    }

    /**
     * Persist failure/dead status in REQUIRES_NEW so trace survives outer rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStripeEventStatusWithAttempt(String eventId,
                                        String type,
                                        StripeEventStatus status,
                                        String lastError,
                                        int attempt,
                                        int attemptInc) {
        // Upsert handles both "row exists" and "row missing after conflict" cases.
        stripeEventRepository.upsertFailStatus(eventId, type, status.name(),
                lastError, LocalDateTime.now(), attempt, attemptInc);
    }
}

