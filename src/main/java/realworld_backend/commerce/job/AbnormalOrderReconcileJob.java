package realworld_backend.commerce.job;


import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.service.AbnormalOrderService;

@RequiredArgsConstructor
@Component
/**
 * Scheduler wrapper for abnormal-order reconciliation.
 */
public class AbnormalOrderReconcileJob {

    private final AbnormalOrderService abnormalOrderService;
    // Fixed-delay polling for abnormal-order retries.
    @Scheduled(fixedDelay = 300000)
    public void run() {
        abnormalOrderService.retryAbnormalOrder();
    }

}

