package realworld_backend.service.CommerceService;

import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.dto.Exception.BizException;
import realworld_backend.dto.Exception.ErrorCode;
import realworld_backend.model.commerceModule.Payment;
import realworld_backend.model.commerceModule.PaymentStatus;
import realworld_backend.repository.CommerceRepository.PaymentRepository;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * Insert initial payment record when order flow starts.
     */
    public void recordInit(String orderNo) {
        Payment payment = new Payment();
        payment.setOrderNo(orderNo);
        payment.setStatus(PaymentStatus.INIT);
        payment.setProvider("Stripe");
        paymentRepository.save(payment);
    }

    /**
     * Record Stripe session creation failure details.
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
     * Transition payment to PROCESSING with Stripe session id.
     */
    public void recordProcessing(String orderNo, String sessionId) {
        Payment payment = paymentRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.USER_JSON_ERROR));
        payment.setSessionId(sessionId);
        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);
    }

    /**
     * Transition payment to PAYING with Stripe session id.
     */
    public void recordPaying(String orderNo, String sessionId) {
        Payment payment = paymentRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.USER_JSON_ERROR));
        payment.setSessionId(sessionId);
        payment.setStatus(PaymentStatus.PAYING);
        paymentRepository.save(payment);
    }
}
