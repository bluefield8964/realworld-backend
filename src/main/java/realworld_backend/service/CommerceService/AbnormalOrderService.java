package realworld_backend.service.CommerceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.model.commerceModule.AbnormalOrderStatus;
import realworld_backend.repository.CommerceRepository.AbnormalOrderRepository;

import java.time.LocalDateTime;
@Slf4j
@EnableScheduling
@Service
@RequiredArgsConstructor
public class AbnormalOrderService {

    private final AbnormalOrderRepository abnormalOrderRepository;

    /**
     * Upsert abnormal order record by unique sessionId.
     * Uses REQUIRES_NEW so failure traces are kept independently.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertAbnormalOrder(
            String orderNo,
            String eventId,
            String eventType,
            String sessionId,
            String reason,
            String errorMessage,
            AbnormalOrderStatus status
    ) {
        LocalDateTime now = LocalDateTime.now();
        String errorLine = "[" + now + "] " + errorMessage;

        abnormalOrderRepository.upsertBySessionId(
                orderNo,
                eventId,
                eventType,
                sessionId,
                reason,
                errorLine,
                status.name(),
                now,   // createdAt
                now,   // lastRetryAt
                now    // nextRetryAt（你也可以改成 now.plusMinutes(5)）
        );
    }


    public void retryAbnormalOrder(String sessionId) {



    }



}
