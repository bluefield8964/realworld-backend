package realworld_backend.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import realworld_backend.auth.domain.exception.AuthException;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestControllerAdvice(basePackages = "realworld_backend.auth.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthControllerAdvice {

    private static final Set<ErrorCode> LOGIN_FAILURE_MASKED_CODES = new LinkedHashSet<>(Set.of(
            ErrorCode.INVALID_CREDENTIALS,
            ErrorCode.INVALID_IDENTIFIER,
            ErrorCode.INVALID_PASSWORD,
            ErrorCode.USER_NOT_FOUND,
            ErrorCode.ACCOUNT_LOCKED,
            ErrorCode.ACCOUNT_DISABLED,
            ErrorCode.ACCOUNT_BANNED,
            ErrorCode.TOO_MANY_ATTEMPTS
    ));

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e,
            HttpServletRequest request
    ) {
        log.error("{} occurred on {}: message={}",
                e.getClass().getSimpleName(),
                request.getRequestURI(),
                e.getMessage(),
                e);
        return ResponseEntity.ok(ApiResponse.error(
                ErrorCode.JSON_ERROR.code(),
                ErrorCode.JSON_ERROR.message()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        log.error("{} occurred on {}: message={}",
                e.getClass().getSimpleName(),
                request.getRequestURI(),
                e.getMessage(),
                e);

        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(fieldError.getField(), k -> new ArrayList<>())
                    .add(fieldError.getDefaultMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errors", errors);
        return ResponseEntity.ok(new ApiResponse<>(422, "validation error", data));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<?>> handleAuthException(AuthException e, HttpServletRequest request) {
        log.error("{} occurred on {}: code={}, message={}",
                e.getClass().getSimpleName(),
                request.getRequestURI(),
                e.getCode(),
                e.getMessage(),
                e);

        if (shouldMaskLoginFailure(request.getRequestURI(), e.getErrorCode())) {
            return ResponseEntity.ok(ApiResponse.error(
                    ErrorCode.INVALID_CREDENTIALS.code(),
                    ErrorCode.INVALID_CREDENTIALS.message()
            ));
        }

        return ResponseEntity.ok(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<?>> handleBizException(BizException e, HttpServletRequest request) {
        log.error("{} occurred on {}: code={}, message={}",
                e.getClass().getSimpleName(),
                request.getRequestURI(),
                e.getErrorCode().getCode(),
                e.getErrorCode().getMessage(),
                e);
        return ResponseEntity.ok(ApiResponse.error(
                e.getErrorCode().getCode(),
                e.getErrorCode().getMessage()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e, HttpServletRequest request) {
        log.error("{} occurred on {}: message={}",
                e.getClass().getSimpleName(),
                request.getRequestURI(),
                e.getMessage(),
                e);
        return ResponseEntity.ok(ApiResponse.error(
                ErrorCode.SYSTEM_ERROR.getCode(),
                ErrorCode.SYSTEM_ERROR.getMessage()
        ));
    }

    private boolean shouldMaskLoginFailure(String requestUri, ErrorCode errorCode) {
        if (requestUri == null || !requestUri.endsWith("/users/login")) {
            return false;
        }
        return LOGIN_FAILURE_MASKED_CODES.contains(errorCode);
    }
}
