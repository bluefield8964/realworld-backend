package realworld_backend.service;

import com.stripe.exception.StripeException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import realworld_backend.dto.Exception.BizException;
import realworld_backend.dto.Exception.ErrorCode;
import realworld_backend.model.Payment;
import realworld_backend.model.PaymentStatus;
import realworld_backend.repository.OrderRepository;
import realworld_backend.repository.PaymentRepository;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public void recordInit(String orderNo) {
        Payment payment = new Payment();
        payment.setOrderNo(orderNo);
        payment.setStatus(PaymentStatus.INIT);
        payment.setProvider("Stripe");
        paymentRepository.save(payment);
    }


    public void recordFail(String orderNo, StripeException stripeException) {
        // if create session successfully，use session.getUrl() and others parameters
        // session.getStatus() normally will be  "open"
        //SAVE SESSION INFO
        Payment payment = paymentRepository.findByOrderNo(orderNo).orElseThrow(() -> new BizException(ErrorCode.USER_JSON_ERROR));
        payment.setStatus(PaymentStatus.FAILED);
        payment.setErrorMsg(stripeException.getMessage());
        payment.setCode(stripeException.getCode());
        payment.setRequestId(stripeException.getRequestId());
        paymentRepository.save(payment);
    }

    public void recordProcessing(String orderNo, String sessionId) {
        Payment payment = paymentRepository.findByOrderNo(orderNo).orElseThrow(() -> new BizException(ErrorCode.USER_JSON_ERROR));
        payment.setTransactionId(sessionId);
        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);

    }

    public void recordPaying(String orderNo, String sessionId) {
        Payment payment = paymentRepository.findByOrderNo(orderNo).orElseThrow(() -> new BizException(ErrorCode.USER_JSON_ERROR));
        payment.setTransactionId(sessionId);
        payment.setStatus(PaymentStatus.PAYING);
        paymentRepository.save(payment);

    }
}


