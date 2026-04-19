package realworld_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import realworld_backend.service.CommerceService.OrderService;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    public final OrderService orderService;

    @PostMapping("/create")
    public String createPayment(@AuthenticationPrincipal Jwt jwt, @RequestParam("productId") Long productId) throws Exception {
        return orderService.orderProcess(jwt, productId);
    }


}
