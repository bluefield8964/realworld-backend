package realworld_backend.controller;

import org.springframework.data.repository.query.Param;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import realworld_backend.service.OrderService;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    public final OrderService orderService;

    public PaymentController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/create")
    public String createPayment(@AuthenticationPrincipal Jwt jwt, @Param("productId") Long productId) throws Exception {
        return orderService.orderProcess(jwt, productId);
    }

    @PostMapping("/webhook")
    public void handleWebhook(@RequestBody String payload) {

        // 验证签名（很重要）
        // 判断支付成功
        // 更新订单状态
    }
}
