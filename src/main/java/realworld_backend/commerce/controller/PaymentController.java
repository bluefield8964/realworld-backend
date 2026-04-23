package realworld_backend.commerce.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import realworld_backend.commerce.service.OrderService;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
/**
 * Payment creation entrypoint.
 * Delegates business orchestration to OrderService.
 */
public class PaymentController {

    public final OrderService orderService;

    /**
     * Create or reuse a payable order and return the checkout URL.
     */
    @PostMapping("/create")
    public String createPayment(@AuthenticationPrincipal Jwt jwt, @RequestParam("productId") Long productId) throws Exception {
        return orderService.orderProcess(jwt, productId);
    }
}

