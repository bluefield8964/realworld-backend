package realworld_backend.auth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import realworld_backend.auth.api.request.*;
import realworld_backend.auth.api.response.LoginResponse;
import realworld_backend.auth.api.response.RefreshResponse;
import realworld_backend.auth.application.command.LogoutCommand;
import realworld_backend.auth.application.command.PasswordLoginCommand;
import realworld_backend.auth.application.result.LoginResult;
import realworld_backend.auth.application.useCase.PasswordLoginUseCase;
import realworld_backend.auth.domain.exception.AuthException;
import realworld_backend.auth.domain.exception.UserAuthException;
import realworld_backend.auth.domain.model.TokenPair;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.common.web.resolver.CurrentUser;

/**
 * Exposes authentication endpoints for login, registration, logout, and refresh rotation.
 */
import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final PasswordLoginUseCase passwordLoginUseCase;
    private final IpResolver ipResolver;

    @PostMapping("/users/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Validated(LoginGroup.class) LoginRequest request,
                                               HttpServletRequest httpRequest, HttpServletResponse response) {
        if (request.getIdentifier() == null || request.getIdentifier().trim().isEmpty()) {
            throw new UserAuthException(ErrorCode.INVALID_IDENTIFIER);
        }
        String identifier = request.getIdentifier().trim();

        String ip = ipResolver.resolve(httpRequest);
        PasswordLoginCommand command;
        if (identifier.contains("@")) {
            command = new PasswordLoginCommand(
                    identifier,
                    null,
                    request.getPassword(),
                    Boolean.TRUE.equals(request.getRememberMe()),
                    request.getDeviceId(),
                    ip,
                    httpRequest.getHeader("User-Agent"),
                    UUID.randomUUID().toString()
            );
        } else {
            command = new PasswordLoginCommand(
                    null,
                    identifier,
                    request.getPassword(),
                    Boolean.TRUE.equals(request.getRememberMe()),
                    request.getDeviceId(),
                    ip,
                    httpRequest.getHeader("User-Agent"),
                    UUID.randomUUID().toString()
            );
        }

        LoginResult result = passwordLoginUseCase.login(command);
        Cookie refreshCookie = new Cookie("refresh_token", result.getTokenPair().getRefreshToken());
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(true); // Must be true in HTTPS
        refreshCookie.setPath("/auth/users/refresh");
        refreshCookie.setMaxAge(15 * 24 * 60 * 60);

        response.addCookie(refreshCookie);
        return ResponseEntity.ok(LoginResponse.from(result));
    }

    @PostMapping("/users/register")
    public ResponseEntity<LoginResponse> register
            (@RequestBody @Validated(RegisterGroup.class) LoginRequest request,
             HttpServletRequest httpRequest, HttpServletResponse response) {
        String ip = ipResolver.resolve(httpRequest);
        PasswordLoginCommand command = new PasswordLoginCommand(
                request.getEmail().trim(),
                request.getUsername(),
                request.getPassword(),
                Boolean.TRUE.equals(request.getRememberMe()),
                request.getDeviceId(),
                ip,
                httpRequest.getHeader("User-Agent"),
                UUID.randomUUID().toString()
        );

        PasswordLoginCommand registerCommand = passwordLoginUseCase.register(command);
        // auto login
        LoginResult result = passwordLoginUseCase.login(registerCommand);
        Cookie refreshCookie = new Cookie("refresh_token", result.getTokenPair().getRefreshToken());
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(true); // Must be true in HTTPS
        refreshCookie.setPath("/auth/users/refresh");
        refreshCookie.setMaxAge(15 * 24 * 60 * 60);
        response.addCookie(refreshCookie);

        return ResponseEntity.ok(LoginResponse.from(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<LoginResponse> logout
            (@CurrentUser CurrentAuthUser authUser,
             HttpServletRequest httpRequest, HttpServletResponse response) {
        String ip = ipResolver.resolve(httpRequest);
        passwordLoginUseCase.logout(new LogoutCommand(UUID.randomUUID().toString()
                , httpRequest.getHeader("User-Agent"), ip,
                authUser.userId(), authUser.sessionId()));
        Cookie refreshCookie = new Cookie("refresh_token", "");
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(true);
        refreshCookie.setPath("/auth/users/refresh");
        refreshCookie.setMaxAge(0);
        response.addCookie(refreshCookie);

        return ResponseEntity.noContent().build();
    }


    @PostMapping("/users/refresh")
    public ResponseEntity<RefreshResponse> refreshTokenRotation
            (HttpServletRequest httpRequest, HttpServletResponse response) {
        try {
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
            TokenPair tokenPair = passwordLoginUseCase.refreshTokenRotation(refreshToken, UUID.randomUUID().toString()
                    , httpRequest.getHeader("User-Agent"), ip
            );

            response.addHeader(
                    "Set-Cookie",
                    "refresh_token=" + tokenPair.getRefreshToken() +
                            "; HttpOnly; Secure; Path=/auth/users/refresh; Max-Age=" + (15 * 24 * 60 * 60) +
                            "; SameSite=None;"
            );

            return ResponseEntity.ok(RefreshResponse.from(tokenPair));
        } catch (UserAuthException e) {
            throw e;
        }

    }
}
