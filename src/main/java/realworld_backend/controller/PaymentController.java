package realworld_backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import realworld_backend.service.PaymentService;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    public final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create")
    public String createPayment(@AuthenticationPrincipal Jwt jwt) throws Exception {
        return paymentService.createPayment(jwt);
    }

    @PostMapping("/webhook")
    public void handleWebhook(@RequestBody String payload) {

        // 验证签名（很重要）
        // 判断支付成功
        // 更新订单状态
    }
}
