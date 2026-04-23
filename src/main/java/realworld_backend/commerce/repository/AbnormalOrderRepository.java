package realworld_backend.commerce.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import realworld_backend.commerce.model.AbnormalOrder;
import realworld_backend.commerce.model.AbnormalOrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AbnormalOrderRepository extends JpaRepository<AbnormalOrder, Long> {
    Optional<AbnormalOrder> findBySessionId(String sessionId);


    @Modifying
    @Query(value = """
INSERT INTO abnormalOrders
(order_no, event_id, event_type, session_id, request_id, provider, reason, error_message, retry_count, status, created_at, updated_at, last_retry_at, next_retry_at)
VALUES
(:orderNo, :eventId, :eventType, :sessionId, :requestId, :provider, :reason, :errorLine, 1, :status, :createdAt, NOW(), :lastRetryAt, :nextRetryAt)
ON DUPLICATE KEY UPDATE
    order_no = VALUES(order_no),
    event_id = VALUES(event_id),
    event_type = VALUES(event_type),
    request_id = VALUES(request_id),
    provider = VALUES(provider),
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
            @Param("requestId") String requestId,
            @Param("provider") String provider,
            @Param("reason") String reason,
            @Param("errorLine") String errorLine,
            @Param("status") String status,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("lastRetryAt") LocalDateTime lastRetryAt,
            @Param("nextRetryAt") LocalDateTime nextRetryAt
    );

    @Query("""
SELECT a
FROM AbnormalOrder a
WHERE a.status IN (
    AbnormalOrderStatus.ORDER_MISSING,
    AbnormalOrderStatus.PAYMENT_MISSING,
    AbnormalOrderStatus.RETRY_EXHAUSTED
)
AND a.nextRetryAt <= :now
ORDER BY a.nextRetryAt ASC
""")
    List<AbnormalOrder> findRetryCandidates(
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    boolean existsBySessionIdAndStatus(String sessionId, AbnormalOrderStatus status);

    @Modifying
    @Query("""
UPDATE AbnormalOrder a
SET a.status = :status,
    a.updatedAt = :now
WHERE a.sessionId = :sessionId
""")
    int updateStatusBySessionId(
            @Param("sessionId") String sessionId,
            @Param("status") AbnormalOrderStatus status,
            @Param("now") LocalDateTime now
    );
}

