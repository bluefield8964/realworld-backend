package realworld_backend.article.controller;


import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import realworld_backend.article.service.UserService;
import realworld_backend.auth.api.reqeust.CurrentAuthUser;
import realworld_backend.auth.security.TokenTool;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.dto.responseBody.UserResponse;
import realworld_backend.common.web.resolver.CurrentUser;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class userController {

    private final UserService userService;
    private final TokenTool tokenTool;


    @PutMapping("/user")
    public ApiResponse<UserResponse> updateUser(@RequestBody JsonNode requestBody, HttpServletRequest httpUserRequest,@CurrentUser CurrentAuthUser currentUser) {

        UserResponse loginUser = userService.updateCurrentUser(requestBody, httpUserRequest, currentUser);

        return ApiResponse.success(loginUser);
    }

}

