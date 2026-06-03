package realworld_backend.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import realworld_backend.article.model.UserProfile;
import realworld_backend.article.repository.UserProfileRepository;
import realworld_backend.article.service.UserService;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.auth.api.request.IpResolver;
import realworld_backend.auth.application.command.LogoutCommand;
import realworld_backend.auth.application.command.PasswordLoginCommand;
import realworld_backend.auth.application.result.LoginResult;
import realworld_backend.auth.application.useCase.PasswordLoginUseCase;
import realworld_backend.auth.domain.exception.AuthException;
import realworld_backend.auth.domain.model.TokenPair;
import realworld_backend.auth.domain.model.UserAuthProfile;
import realworld_backend.auth.repository.UserAuthProfileRepository;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.dto.responseBody.UserResponse;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.common.web.resolver.CurrentUser;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RealWorld-compatible auth endpoints kept for legacy hurl specs and older frontend integration.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LegacyAuthController {
    private final PasswordLoginUseCase passwordLoginUseCase;
    private final IpResolver ipResolver;
    private final UserService userService;
    private final UserAuthProfileRepository userAuthProfileRepository;
    private final UserProfileRepository userProfileRepository;
    @Value("${security.cookie.secure:true}")
    private boolean secureCookie;

    @PostMapping("/users/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @RequestBody JsonNode requestBody,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        JsonNode userNode = requiredUserNode(requestBody);
        String email = null;
        String username =  null;
        if (userNode.get("email")!=null) {
            email = requiredText(userNode, "email", ErrorCode.INVALID_IDENTIFIER);
        }else {
            username = requiredText(userNode, "username", ErrorCode.INVALID_IDENTIFIER);
        }

        String password = requiredText(userNode, "password", ErrorCode.INVALID_PASSWORD);

        String ip = ipResolver.resolve(httpRequest);
        String deviceId = UUID.randomUUID().toString();
        PasswordLoginCommand command = new PasswordLoginCommand(
                email,
                username,
                password,
                false,
                deviceId,
                ip,
                httpRequest.getHeader("User-Agent"),
                UUID.randomUUID().toString()
        );

        LoginResult result = passwordLoginUseCase.login(command);
        ensureArticleUserProfile(command);
        TokenPair tokenPair = result.getTokenPair();
        appendRefreshCookie(response, tokenPair);
        UserResponse userResponse = buildLegacyUserResponse(result, email, null);
        return ResponseEntity.ok(ApiResponse.success(wrapUser(userResponse)));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(
            @RequestBody JsonNode requestBody,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        JsonNode userNode = requiredUserNode(requestBody);
        String username = requiredText(userNode, "username", ErrorCode.INVALID_USERNAME);
        String email = requiredText(userNode, "email", ErrorCode.INVALID_EMAIL);
        String password = requiredText(userNode, "password", ErrorCode.INVALID_PASSWORD);

        String ip = ipResolver.resolve(httpRequest);
        String deviceId = UUID.randomUUID().toString();
        PasswordLoginCommand command = new PasswordLoginCommand(
                email,
                username,
                password,
                false,
                deviceId,
                ip,
                httpRequest.getHeader("User-Agent"),
                UUID.randomUUID().toString()
        );

        PasswordLoginCommand registerCommand = passwordLoginUseCase.register(command);
        LoginResult result = passwordLoginUseCase.login(registerCommand);
        ensureArticleUserProfile(registerCommand);
        TokenPair tokenPair = result.getTokenPair();
        appendRefreshCookie(response, tokenPair);
        UserResponse userResponse = buildLegacyUserResponse(result, email, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(wrapUser(userResponse)));
    }

    @PostMapping("/users/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {

        Cookie[] cookies = httpRequest.getCookies();
        if (cookies == null) {
            throw new AuthException(ErrorCode.TOKEN_MISSING);
        }

        String refreshToken = Arrays.stream(cookies)
                .filter(c -> "refresh_token".equals(c.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElseThrow(() -> new AuthException(ErrorCode.TOKEN_MISSING));

        String ip = ipResolver.resolve(httpRequest);
        TokenPair tokenPair = passwordLoginUseCase.refreshTokenRotation(
                refreshToken,
                UUID.randomUUID().toString(),
                httpRequest.getHeader("User-Agent"),
                ip
        );
        appendRefreshCookie(response, tokenPair);


        UserResponse userResponse = new UserResponse();
        userResponse.setBearer(tokenPair.getAccessToken());
        return ResponseEntity.ok(ApiResponse.success(wrapUser(userResponse)));

    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CurrentUser CurrentAuthUser authUser,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        String ip = ipResolver.resolve(httpRequest);
        passwordLoginUseCase.logout(new LogoutCommand(
                UUID.randomUUID().toString(),
                httpRequest.getHeader("User-Agent"),
                ip,
                authUser.userId(),
                authUser.sessionId()
        ));

        Cookie refreshCookie = new Cookie("refresh_token", "");
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(secureCookie);
        refreshCookie.setPath("/api/users/refresh");
        refreshCookie.setMaxAge(0);
        response.addCookie(refreshCookie);
        return ResponseEntity.noContent().build();
    }

    private JsonNode requiredUserNode(JsonNode requestBody) {
        JsonNode userNode = requestBody == null ? null : requestBody.get("user");
        if (userNode == null || userNode.isNull()) {
            throw new BizException(ErrorCode.USER_JSON_ERROR);
        }
        return userNode;
    }

    private String requiredText(JsonNode node, String fieldName, ErrorCode errorCode) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || field.asText().trim().isEmpty()) {
            throw new BizException(errorCode);
        }
        return field.asText().trim();
    }

    private void appendRefreshCookie(HttpServletResponse response, TokenPair tokenPair) {
        Cookie refreshCookie = new Cookie("refresh_token", tokenPair.getRefreshToken());
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(secureCookie);
        refreshCookie.setPath("/api/users/refresh");
        refreshCookie.setMaxAge(15 * 24 * 60 * 60);
        response.addCookie(refreshCookie);
    }


    private UserResponse buildLegacyUserResponse(LoginResult result, String email, String username) {
        UserProfile profile = email != null
                ? userService.findByEmail(email).orElseGet(() -> username == null ? null : userService.findByUsername(username).orElse(null))
                : username == null ? null : userService.findByUsername(username).orElse(null);

        UserResponse response = profile == null ? new UserResponse() : new UserResponse(profile);
        if (profile == null) {
            UserAuthProfile authProfile = email != null
                    ? userAuthProfileRepository.findByEmail(email)
                    : username == null ? null : userAuthProfileRepository.findByUsername(username);
            if (authProfile == null && username != null) {
                authProfile = userAuthProfileRepository.findByUsername(username);
            }
            response.setUsername(authProfile != null ? authProfile.getUsername() : username);
            response.setEmail(authProfile != null ? authProfile.getEmail() : email);
        }
        String accessToken = result.getTokenPair().getAccessToken();
        response.setBearer(accessToken);
        return response;
    }

    private void ensureArticleUserProfile(PasswordLoginCommand command) {
        UserAuthProfile authProfile = command.email() != null
                ? userAuthProfileRepository.findByEmail(command.email())
                : command.username() == null ? null : userAuthProfileRepository.findByUsername(command.username());
        if (authProfile == null) {
            return;
        }

        userProfileRepository.findByUserAuthId(authProfile.getId()).orElseGet(() ->
                userProfileRepository.save(UserProfile.builder()
                        .userAuthId(authProfile.getId())
                        .username(authProfile.getUsername())
                        .email(authProfile.getEmail())
                        .bio(null)
                        .image(null)
                        .build())
        );
    }

    private Map<String, Object> wrapUser(UserResponse userResponse) {
        Map<String, Object> data = new HashMap<>();
        data.put("user", userResponse);
        return data;
    }
}
