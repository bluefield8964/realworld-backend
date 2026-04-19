package realworld_backend.repository.CommerceRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import realworld_backend.model.commerceModule.AbnormalOrder;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AbnormalOrderRepository extends JpaRepository<AbnormalOrder, Long> {
    Optional<AbnormalOrder> findBySessionId(String sessionId);


    @Modifying
    @Query(value = """
INSERT INTO abnormalOrders
(order_no, event_id, event_type, session_id, reason, error_message, retry_count, status, created_at, updated_at, last_retry_at, next_retry_at)
VALUES
(:orderNo, :eventId, :eventType, :sessionId, :reason, :errorLine, 1, :status, :createdAt, NOW(), :lastRetryAt, :nextRetryAt)
ON DUPLICATE KEY UPDATE
    order_no = VALUES(order_no),
    event_id = VALUES(event_id),
    event_type = VALUES(event_type),
    reason = VALUES(reason),
    status = VALUES(status),
    retry_count = retry_count + 1,
    error_message = CONCAT(
        IFNULL(error_message, ''),
        CASE WHEN error_message IS NULL OR error_message = '' THEN '' ELSE '\\n' END,
        VALUES(error_message)
    ),
    updated_at = NOW(),
    last_retry_at = :lastRetryAt,
    next_retry_at = :nextRetryAt
""", nativeQuery = true)
    int upsertBySessionId(
            @Param("orderNo") String orderNo,
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("sessionId") String sessionId,
            @Param("reason") String reason,
            @Param("errorLine") String errorLine,
            @Param("status") String status,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("lastRetryAt") LocalDateTime lastRetryAt,
            @Param("nextRetryAt") LocalDateTime nextRetryAt
    );
}
