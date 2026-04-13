package realworld_backend.controller;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import realworld_backend.dto.Exception.BizException;
import realworld_backend.dto.Exception.ErrorCode;
import realworld_backend.dto.requestBody.UserRequest;
import realworld_backend.dto.responseBody.ApiResponse;
import realworld_backend.dto.responseBody.UserResponse;
import realworld_backend.insolver.CurrentUser;
import realworld_backend.model.User;
import realworld_backend.service.UserService;
import realworld_backend.tool.TokenTool;
import tools.jackson.databind.JsonNode;

import java.text.ParseException;

@RestController
@RequestMapping("/api")
public class userController {

    private final UserService userService;
    private final TokenTool tokenTool;

    public userController(UserService userService, TokenTool tokenTool) {
        this.tokenTool = tokenTool;
        this.userService = userService;
    }

    @PostMapping("/users")
    public ApiResponse<UserResponse> register(@RequestBody @Valid UserRequest userRequest, HttpServletResponse response) {

        User registerUser = userService.register(userRequest);
        String token = tokenTool.generateToken(registerUser);
        response.setHeader("authentication", "Bearer " + token);
        UserResponse userResponse = new UserResponse(registerUser);
        userResponse.setBearer(token);
        return ApiResponse.success(userResponse);
    }

    @PostMapping("/users/login")
    public ApiResponse<UserResponse> login(@RequestBody @Valid UserRequest userRequest, HttpServletResponse response) {

        UserResponse loginUser = userService.login(userRequest, response);

        return ApiResponse.success(loginUser);
    }

    @GetMapping("/user")
    public ApiResponse<UserResponse> getCurrentUser(HttpServletRequest userRequest, @CurrentUser User user) {
        UserResponse loginUser = userService.getCurrentUser(userRequest, user);
        return ApiResponse.success(loginUser);
    }

    @PutMapping("/api/user")
    public ApiResponse<UserResponse> updateUser(@RequestBody JsonNode requestBody, HttpServletRequest httpUserRequest, @AuthenticationPrincipal Jwt jwt) {

        UserResponse loginUser = userService.updateCurrentUser(requestBody, httpUserRequest, jwt);

        return ApiResponse.success(loginUser);
    }


    //should be the only one controll layer to  grant token ,
    @PostMapping("/logout")
    public ApiResponse<UserResponse> logout(@AuthenticationPrincipal Jwt jwt, @CurrentUser User user, HttpServletRequest userRequest) throws ParseException {
        userService.logout(user, userRequest);
        return ApiResponse.success(null);
    }

    @PostMapping("/api/auth/refresh")
    public ApiResponse<UserResponse> refreshToken(@CookieValue(name = "refreshToken", required = false) String refreshToken, @RequestParam("userId") Long userId) {
        if (refreshToken == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        String newAccessToken = userService.refreshShortTermToken(refreshToken, userId);

        UserResponse userResponse = new UserResponse();
        userResponse.setBearer(newAccessToken);

        return ApiResponse.success(userResponse);
    }


}
