package realworld_backend.tool;


import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import realworld_backend.service.CommerceService.AbnormalOrderService;

@RequiredArgsConstructor
@Component
public class AbnormalOrderReconcileJob {

    private final AbnormalOrderService abnormalOrderService;
    @Scheduled(fixedDelay = 300000) // 5分钟
    public void run() {

    }

}
