package realworld_backend.commerce.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import realworld_backend.commerce.model.log.WebhookIncident;
import realworld_backend.commerce.model.log.WebhookIncidentStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookIncidentRepository extends JpaRepository<WebhookIncident, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w
            FROM WebhookIncident w
            WHERE w.dedupeKey = :dedupeKey
            """)
    Optional<WebhookIncident> findByDedupeKeyForUpdate(@Param("dedupeKey") String dedupeKey);

    Optional<WebhookIncident> findByDedupeKey(String dedupeKey);

    List<WebhookIncident> findTop100ByStatusOrderByCreatedAtAsc(WebhookIncidentStatus status);
}
