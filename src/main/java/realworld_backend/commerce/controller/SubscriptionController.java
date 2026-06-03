package realworld_backend.commerce.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.commerce.model.subscription.enums.ProviderType;
import realworld_backend.commerce.service.subscription.SubscriptionCatalogService;
import realworld_backend.commerce.service.subscription.SubscriptionQueryService;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.web.resolver.CurrentUser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-model endpoint for the current user's subscription view.
 */
@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
public class SubscriptionController {
    private final SubscriptionQueryService subscriptionQueryService;
    private final SubscriptionCatalogService subscriptionCatalogService;

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> getCurrentSubscription(
            @CurrentUser(required = true) CurrentAuthUser currentUser
    ) {
        SubscriptionQueryService.SubscriptionMeView subscription =
                subscriptionQueryService.getCurrentUserSubscription(currentUser);
        Map<String, Object> data = new HashMap<>();
        data.put("subscription", subscription);
        return ApiResponse.success(data);
    }

    @GetMapping("/options")
    public ApiResponse<Map<String, Object>> getAvailableSubscriptionOptions(
            @CurrentUser(required = true) CurrentAuthUser currentUser,
            @RequestParam(value = "provider", defaultValue = "STRIPE") ProviderType provider
    ) {
        List<SubscriptionCatalogService.SubscriptionPlanOption> options =
                subscriptionCatalogService.getAvailableOptions(provider);
        Map<String, Object> data = new HashMap<>();
        data.put("provider", provider.name());
        data.put("plans", options);
        return ApiResponse.success(data);
    }
}
