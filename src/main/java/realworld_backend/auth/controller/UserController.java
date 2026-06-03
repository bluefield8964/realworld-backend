package realworld_backend.auth.controller;


import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import realworld_backend.article.service.UserService;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.dto.responseBody.UserResponse;
import realworld_backend.common.web.resolver.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles profile updates for the currently authenticated user.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/user")
    public ApiResponse<Map<String, Object>> getCurrentUser(
            HttpServletRequest httpUserRequest,
            @CurrentUser CurrentAuthUser currentUser
    ) {
        UserResponse currentUserView = userService.getCurrentUser(httpUserRequest, currentUser);
        Map<String, Object> data = new HashMap<>();
        data.put("user", currentUserView);
        return ApiResponse.success(data);
    }

    @PutMapping("/user")
    // Updates the current user profile using the authenticated user context.
    public ApiResponse<Map<String, Object>> updateUser(@RequestBody JsonNode requestBody, HttpServletRequest httpUserRequest,@CurrentUser CurrentAuthUser currentUser) {
        UserResponse loginUser = userService.updateCurrentUser(requestBody, httpUserRequest, currentUser);
        Map<String, Object> data = new HashMap<>();
        data.put("user", loginUser);
        return ApiResponse.success(data);
    }
}
