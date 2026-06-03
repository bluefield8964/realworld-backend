package realworld_backend.commerce.controller;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.commerce.model.entitlement.EntitlementGrant;
import realworld_backend.commerce.service.entitlement.EntitlementFacade;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.web.resolver.CurrentUser;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-model endpoints for the current user's entitlement view and access checks.
 */
@RestController
@RequestMapping("/api/entitlement")
@RequiredArgsConstructor
public class EntitlementController {
    private final EntitlementFacade entitlementFacade;

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> getCurrentUserEntitlements(
            @CurrentUser(required = true) CurrentAuthUser currentUser
    ) {
        List<EntitlementGrant> entitlements = entitlementFacade.listActiveEntitlements(currentUser.userId());
        Map<String, Object> data = new HashMap<>();
        data.put("entitlements", entitlements);
        return ApiResponse.success(data);
    }

    @GetMapping("/bundles/access")
    public ApiResponse<Map<String, Object>> hasAnyBundleAccess(
            @CurrentUser(required = true) CurrentAuthUser currentUser
    ) {
        boolean hasAccess = entitlementFacade.hasAnyBundleAccess(currentUser.userId(), UtcTimeMapper.nowUtc());
        Map<String, Object> data = new HashMap<>();
        data.put("hasAccess", hasAccess);
        return ApiResponse.success(data);
    }

    @GetMapping("/bundles/{bundleCode}/access")
    public ApiResponse<Map<String, Object>> hasBundleAccess(
            @CurrentUser(required = true) CurrentAuthUser currentUser,
            @PathVariable("bundleCode") String bundleCode
    ) {
        boolean hasAccess = entitlementFacade.hasBundleAccess(currentUser.userId(), bundleCode, UtcTimeMapper.nowUtc());
        Map<String, Object> data = new HashMap<>();
        data.put("bundleCode", bundleCode);
        data.put("hasAccess", hasAccess);
        return ApiResponse.success(data);
    }

    @GetMapping("/content/{contentId}/access")
    public ApiResponse<Map<String, Object>> hasContentAccess(
            @CurrentUser(required = true) CurrentAuthUser currentUser,
            @PathVariable("contentId") String contentId
    ) {
        boolean hasAccess = entitlementFacade.hasContentAccess(currentUser.userId(), contentId, UtcTimeMapper.nowUtc());
        Map<String, Object> data = new HashMap<>();
        data.put("contentId", contentId);
        data.put("hasAccess", hasAccess);
        return ApiResponse.success(data);
    }
}

