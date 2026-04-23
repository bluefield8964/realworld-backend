package realworld_backend.commerce.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import realworld_backend.commerce.model.StripeEvent;
import realworld_backend.commerce.model.StripeEventStatus;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface StripeEventRepository extends JpaRepository<StripeEvent, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from StripeEvent e where e.eventId = :eventId")
    Optional<StripeEvent> findByIdForUpdate(@Param("eventId") String eventId);

    @Modifying
    @Query("""
        update StripeEvent e
        set e.status = :status,
            e.lastError = :lastError,
            e.eventHandledAt = :now,
            e.attempts = e.attempts + 1
        where e.eventId = :eventId
    """)
    int updateStatusAndIncAttempt(
            @Param("eventId") String eventId,
            @Param("status") StripeEventStatus status,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("""
        update StripeEvent e
        set e.status = :status,
            e.lastError = :lastError,
            e.eventHandledAt = :now
        where e.eventId = :eventId
    """)
    int updateStatusNoAttempt(
            @Param("eventId") String eventId,
            @Param("status") StripeEventStatus status,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query(value = """
    INSERT INTO stripeEvents (event_id, type, status, last_error, event_handled_at, attempts)
    VALUES (:eventId, :type, :status, :lastError, :now, :attempts)
    ON DUPLICATE KEY UPDATE
        status = VALUES(status),
        last_error = VALUES(last_error),
        event_handled_at = VALUES(event_handled_at),
        attempts = attempts + :attemptsInc
    """, nativeQuery = true)
    int upsertFailStatus(
            @Param("eventId") String eventId,
            @Param("type") String type,
            @Param("status") String status,   // 娴?StripeEventStatus.FAILED/DEAD.name()
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now,
            @Param("attempts") int attempts,      // insert閺冭泛鍨垫慨瀣偓纭风礉瀵ら缚顔?1
            @Param("attemptsInc") int attemptsInc // update閺冭泛顤冮柌蹇ョ礉瀵ら缚顔?1
    );
}
