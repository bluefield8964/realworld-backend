package realworld_backend.service.CommerceService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.model.commerceModule.StripeEvent;
import realworld_backend.model.commerceModule.StripeEventStatus;
import realworld_backend.repository.CommerceRepository.StripeEventRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StripeEventService {
    private final StripeEventRepository stripeEventRepository;

    /**
     * Reserve event row in PROCESSING state.
     * Duplicate key is handled by caller as idempotency signal.
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
     * Mark event as successful in current transaction context.
     */
    public void markSuccessStatusAndIncAttempt(String eventId, StripeEventStatus status, String lastError) {
        stripeEventRepository.updateStatusAndIncAttempt(eventId, status, lastError, LocalDateTime.now());
    }

    /**
     * Failure path is persisted in an independent transaction.
     * This guarantees failure trace even when outer transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailStatusNoAttempt(String eventId,
                                        String type,
                                        StripeEventStatus status,
                                        String lastError,
                                        int attempt,
                                        int attemptInc) {
        stripeEventRepository.upsertFailStatus(eventId, type, status.name(),
                lastError, LocalDateTime.now(), attempt, attemptInc);
    }
}
