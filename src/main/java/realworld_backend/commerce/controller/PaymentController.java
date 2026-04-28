package realworld_backend.commerce.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.commerce.model.subscription.enums.ProviderType;
import realworld_backend.commerce.service.order.OrderService;
import realworld_backend.commerce.service.subscription.SubscriptionService;
import realworld_backend.common.web.resolver.CurrentUser;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
/**
 * Checkout creation entrypoint for both one-time orders and subscriptions.
 */
public class PaymentController {

    private final OrderService orderService;
    private final SubscriptionService subscriptionService;
    /**
     * Create or reuse a payable order and return the checkout URL.
     */
    @PostMapping("/createOrder")
    public String createOrderCheckout(@CurrentUser CurrentAuthUser authUser, @RequestParam("provider") String provider, @RequestParam("productId") Long productId) throws Exception {
        return orderService.createOrReuseOrderCheckout(authUser, provider, productId);
    }

    /**
     * Create or reuse a subscription checkout and return the checkout URL.
     */
    @PostMapping("/createSubscription")
    public String createSubscriptionCheckout(@CurrentUser CurrentAuthUser authUser, @RequestParam("provider") ProviderType provider, @RequestParam("planCode") String planCode) throws Exception {
        return subscriptionService.createOrReuseSubscriptionCheckout(authUser, provider, planCode);
    }
}


