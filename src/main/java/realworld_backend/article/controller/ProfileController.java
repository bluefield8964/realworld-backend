package realworld_backend.article.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import realworld_backend.article.service.ProfileService;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.dto.responseBody.ProfileResponse;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.common.web.resolver.CurrentUser;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService profileService;

    @GetMapping("/profiles/{username}")
    public ResponseEntity<ApiResponse<?>> getProfile(
            @PathVariable String username,
            @CurrentUser CurrentAuthUser currentUser // could be null, as didnt login
    ) {
        try {
            ProfileResponse response = profileService.getProfile(username, currentUser);
            Map<String, Object> data = new HashMap<>();
            data.put("profile", response);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (BizException e) {
            return toHttpError(e);
        }
    }

    @PostMapping("/profiles/{username}/follow")
    public ResponseEntity<ApiResponse<?>> followUser(
            @PathVariable String username,
            @CurrentUser(required = true) CurrentAuthUser currentUser // must be existed
    ) {
        try {
            ProfileResponse response = profileService.follow(username, currentUser);
            Map<String, Object> data = new HashMap<>();
            data.put("profile", response);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (BizException e) {
            return toHttpError(e);
        }
    }

    @DeleteMapping("/profiles/{username}/follow")
    public ResponseEntity<ApiResponse<?>> unfollowUser(
            @PathVariable String username,
            @CurrentUser(required = true) CurrentAuthUser currentUser
    ) {
        try {
            ProfileResponse response = profileService.unfollow(username, currentUser);
            Map<String, Object> data = new HashMap<>();
            data.put("profile", response);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (BizException e) {
            return toHttpError(e);
        }
    }

    private ResponseEntity<ApiResponse<?>> toHttpError(BizException e) {
        if (e.getErrorCode() == ErrorCode.FOLLOWING_NOT_FOUND || e.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
        }
        if (e.getErrorCode() == ErrorCode.CANNOT_FOLLOW_SELF || e.getErrorCode() == ErrorCode.INVALID_INPUT) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
        }
        if (e.getErrorCode() == ErrorCode.TOKEN_INVALID || e.getErrorCode() == ErrorCode.UNAUTHORIZED) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
        }
        if (e.getErrorCode() == ErrorCode.FORBIDDEN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage()));
    }
}
