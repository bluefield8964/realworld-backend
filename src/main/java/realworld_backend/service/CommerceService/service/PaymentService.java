package realworld_backend.service.CommerceService.service;

import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import realworld_backend.dto.Exception.BizException;
import realworld_backend.dto.Exception.ErrorCode;
import realworld_backend.model.commerceModule.Payment;
import realworld_backend.model.commerceModule.PaymentStatus;
import realworld_backend.repository.CommerceRepository.PaymentRepository;

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
    public void recordInit(String orderNo) {
        Payment payment = new Payment();
        payment.setOrderNo(orderNo);
        payment.setStatus(PaymentStatus.INIT);
        payment.setProvider("Stripe");
        paymentRepository.save(payment);
    }

    /**
     * Persist Stripe session-create failure details.
     */
    public void recordFail(String orderNo, StripeException stripeException) {
        Payment payment = paymentRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.USER_JSON_ERROR));
        payment.setStatus(PaymentStatus.FAILED);
        payment.setErrorMsg(stripeException.getMessage());
        payment.setCode(stripeException.getCode());
        payment.setRequestId(stripeException.getRequestId());
        paymentRepository.save(payment);
    }

    /**
     * Mark payment as PROCESSING with session ID.
     */
    public void recordProcessing(String orderNo, String sessionId) {
        Payment payment = paymentRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.USER_JSON_ERROR));
        payment.setSessionId(sessionId);
        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);
    }

    /**
     * Mark payment as PAYING with session ID.
     */
    public void recordPaying(String orderNo, String sessionId) {
        Payment payment = paymentRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.USER_JSON_ERROR));
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

    public void saveReconcilePayment(Payment payment) {
        // Used by abnormal-order fix path.
        paymentRepository.save(payment);
    }
}
