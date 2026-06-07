package realworld_backend.commerce.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.commerce.model.Order;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.enums.ProviderType;
import realworld_backend.commerce.repository.CustomerSubscriptionRepository;
import realworld_backend.commerce.repository.OrderRepository;
import realworld_backend.commerce.service.order.OrderService;
import realworld_backend.commerce.service.subscription.SubscriptionService;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.web.resolver.CurrentUser;

import java.util.HashMap;
import java.util.Map;

/**
 * Local-only bootstrap endpoints for webhook smoke tests.
 * These endpoints expose the business ids that production checkout APIs intentionally do not return.
 */
@Profile("local")
@RestController
@RequestMapping("/api/debug/bootstrap")
@RequiredArgsConstructor
public class CommerceBootstrapController {
    private final OrderService orderService;
    private final SubscriptionService subscriptionService;
    private final OrderRepository orderRepository;
    private final CustomerSubscriptionRepository customerSubscriptionRepository;

    @PostMapping("/order")
    public ApiResponse<Map<String, Object>> bootstrapOrder(
            @CurrentUser(required = true) CurrentAuthUser authUser,
            @RequestParam(value = "provider", defaultValue = "STRIPE") String provider,
            @RequestParam("productId") Long productId
    ) throws Exception {
        String checkoutUrl = orderService.createOrReuseOrderCheckout(authUser, provider, productId);
        String activeKey = orderService.buildActiveKey(authUser.userId(), productId);
        Order order = orderRepository.findByActiveKey(activeKey).orElse(null);

        Map<String, Object> data = new HashMap<>();
        data.put("provider", provider);
        data.put("productId", productId);
        data.put("activeKey", activeKey);
        data.put("checkoutUrl", checkoutUrl);
        if (order != null) {
            data.put("orderNo", order.getOrderNo());
            data.put("orderStatus", order.getStatus().name());
            data.put("sessionId", order.getSessionId());
            data.put("paymentUrl", order.getPaymentUrl());
            data.put("amount", order.getAmount());
        }
        return ApiResponse.success(data);
    }

    @PostMapping("/subscription")
    public ApiResponse<Map<String, Object>> bootstrapSubscription(
            @CurrentUser(required = true) CurrentAuthUser authUser,
            @RequestParam(value = "provider", defaultValue = "STRIPE") ProviderType provider,
            @RequestParam("planCode") String planCode
    ) throws Exception {
        String checkoutUrl = subscriptionService.createOrReuseSubscriptionCheckout(authUser, provider, planCode);
        String activeKey = subscriptionService.buildActiveKey(authUser.userId(), planCode);
        CustomerSubscription subscription = customerSubscriptionRepository.findByActiveKey(activeKey).orElse(null);

        Map<String, Object> data = new HashMap<>();
        data.put("provider", provider.name());
        data.put("planCode", planCode);
        data.put("activeKey", activeKey);
        data.put("checkoutUrl", checkoutUrl);
        if (subscription != null) {
            data.put("subscriptionNo", subscription.getSubscriptionNo());
            data.put("subscriptionStatus", subscription.getStatus().name());
            data.put("subscriptionUrl", subscription.getSubscriptionUrl());
            data.put("providerSubscriptionId", subscription.getProviderSubscriptionId());
            data.put("providerCustomerId", subscription.getProviderCustomerId());
        }
        return ApiResponse.success(data);
    }
}
