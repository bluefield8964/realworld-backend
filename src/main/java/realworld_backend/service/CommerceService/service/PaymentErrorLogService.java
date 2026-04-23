package realworld_backend.service.CommerceService.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import realworld_backend.model.commerceModule.PaymentErrorLog;
import realworld_backend.repository.CommerceRepository.PaymentErrorLogRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentErrorLogService {
    private final PaymentErrorLogRepository paymentErrorLogRepository;

    public void saveErrorForSupport(String requestId, String errorCode, String message,String provider) {
        // Save to database for later reference when contacting Stripe support
        PaymentErrorLog errorLog = new PaymentErrorLog();
        errorLog.setRequestId(requestId);
        errorLog.setErrorCode(errorCode);
        errorLog.setErrorMessage(message);
        errorLog.setTimestamp(LocalDateTime.now());
        errorLog.setProvider(provider);
        paymentErrorLogRepository.save(errorLog);
        log.error("save error log:{}",errorLog);
    }
}
