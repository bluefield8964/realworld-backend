package realworld_backend.commerce.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.model.Payment;
import realworld_backend.commerce.model.PaymentStatus;
import realworld_backend.commerce.repository.PaymentRepository;
import realworld_backend.commerce.service.core.PaymentChannelException;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Maintains local payment ledger state.
 * Used by both checkout creation flow and reconcile flow.
 */
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * Initial ledger row before Stripe session creation.
     */
    public void recordInit(String orderNo, String provider) {
        Payment payment = new Payment();
        payment.setOrderNo(orderNo);
        payment.setStatus(PaymentStatus.INIT);
        payment.setProvider(provider);
        paymentRepository.save(payment);
    }

    /**
     * Persist Stripe session-create failure details.
     */
    public void recordFail(String orderNo, PaymentChannelException stripeException) {
        Payment payment = paymentRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.PAYMENT_NOT_FOUND));
        payment.setStatus(PaymentStatus.FAILED);
        payment.setErrorMsg(stripeException.getMessage());
        payment.setCode(stripeException.getErrorCode());
        payment.setRequestId(stripeException.getRequestId());
        paymentRepository.save(payment);
    }
    /**
     * Mark payment as PAYING with session ID.
     */
    public void recordPaying(String orderNo, String sessionId) {
        Payment payment = paymentRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.PAYMENT_NOT_FOUND));
        payment.setSessionId(sessionId);
        payment.setStatus(PaymentStatus.PAYING);
        paymentRepository.save(payment);
    }

    public Payment findBySessionId(String sessionId) {
        Optional<Payment> bySessionId = paymentRepository.findBySessionId(sessionId);
        if (bySessionId.isPresent()) {
            Payment payment = bySessionId.get();
            log.info("payment:{} is exist", payment.getOrderNo());
            return payment;
        } else {
            log.info("payment:{} not found", sessionId);
            return null;
        }
    }

    public int markPaidIfNotPaid(String sessionId) {
        java.util.List<PaymentStatus> allowedStatuses =
                List.of(PaymentStatus.PAYING,PaymentStatus.PROCESSING,PaymentStatus.SUCCESS);
        return paymentRepository.markPaidIfNotPaid(sessionId, allowedStatuses);
    }

    public void saveReconcilePayment(Payment payment) {
        // Used by abnormal-order fix path.
        paymentRepository.save(payment);
    }

    public int markFromStatusToStatus(Long id, PaymentStatus fromStatus, PaymentStatus toStatus) {
        return paymentRepository.markFromStatusToStatus(id,fromStatus,toStatus);
    }
}

