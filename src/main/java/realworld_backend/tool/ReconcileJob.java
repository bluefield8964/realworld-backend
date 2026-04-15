package realworld_backend.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import realworld_backend.service.OrderReconcileService;

@Component
@RequiredArgsConstructor
public class ReconcileJob {

    private final OrderReconcileService reconcileService;

    @Scheduled(fixedDelay = 300000) // 5分钟
    public void run() {
        reconcileService.reconcilePendingOrders();
    }
}