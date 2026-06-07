package realworld_backend.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.BusinessEvent;
import realworld_backend.commerce.model.EventStatus;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface BusinessEventRepository extends JpaRepository<BusinessEvent, String> {


    @Modifying
    @Query(value = """
            INSERT INTO business_events (event_id, type, status, last_error, event_handled_at, attempts)
            VALUES (:eventId, :type ,:status, :lastError, :now, 0)
            """, nativeQuery = true)
    int insertProcessing(@Param("eventId") String eventId,
                         @Param("type") BusinessEventType type,
                         @Param("status") EventStatus status,
                         @Param("lastError") String lastError,
                         @Param("now") LocalDateTime now);

    Optional<BusinessEvent> findByEventId(String eventId);

    @Modifying
    @Query("""
                update BusinessEvent e
                set e.status = :status,
                    e.lastError = :lastError,
                    e.eventHandledAt = :now,
                    e.attempts = e.attempts + 1
                where e.eventId = :eventId
            """)
    int updateStatusAndIncAttempt(
            @Param("eventId") String eventId,
            @Param("status") EventStatus status,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
                update BusinessEvent e
                set e.status = :status,
                    e.lastError = :lastError,
                    e.eventHandledAt = :now
                where e.eventId = :eventId
            """)
    int updateStatusNoAttempt(
            @Param("eventId") String eventId,
            @Param("status") EventStatus status,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query(value = """
            INSERT INTO business_events (event_id, type, status, last_error, event_handled_at, attempts)
            VALUES (:eventId, :type, :status, :lastError, :now, :attempts)
            ON DUPLICATE KEY UPDATE
                status = VALUES(status),
                last_error = VALUES(last_error),
                event_handled_at = VALUES(event_handled_at),
                attempts = attempts + :attemptsInc
            """, nativeQuery = true)
    int upsertFailStatus(
            @Param("eventId") String eventId,
            @Param("type") BusinessEventType type,
            @Param("status") String status,   // e.g. BusinessEventStatus.FAILED or DEAD
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now,
            @Param("attempts") int attempts,      // Initial attempts value for insert
            @Param("attemptsInc") int attemptsInc // Increment value for duplicate update
    );


    @Modifying
    @Query(value = """
            UPDATE business_events
            SET status = :status,
                last_error = :lastError,
                event_handled_at = :now,
                attempts = attempts + :attemptsInc
            WHERE event_id = :eventId
              AND status IN ('FAILED', 'PROCESSING')
              AND attempts = :attempts
            """, nativeQuery = true)
    int upsertDeadStatus(
            @Param("eventId") String eventId,
            @Param("type") BusinessEventType type,
            @Param("status") String status,   // e.g. BusinessEventStatus.FAILED or DEAD
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now,
            @Param("attempts") int attempts,      // Initial attempts value for insert
            @Param("attemptsInc") int attemptsInc // Increment value for duplicate update
    );


    @Modifying
    @Query(value = """
            UPDATE business_events
            SET status = :status,
                event_handled_at = :now
            WHERE event_id = :eventId
              AND status IN ('FAILED', 'PROCESSING')
              AND attempts = :attempts
            """, nativeQuery = true)
    int markEventStatusFromFailToProcessing(
            @Param("eventId") String eventId,
            @Param("type") BusinessEventType type,
            @Param("status") String status,   // e.g. BusinessEventStatus.FAILED or DEAD
            @Param("now") LocalDateTime now,
            @Param("attempts") int attempts     // Initial attempts value for insert

    );
}
