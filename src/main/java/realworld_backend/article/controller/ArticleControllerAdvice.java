package realworld_backend.article.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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
@RestControllerAdvice(basePackages = "realworld_backend.article.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ArticleControllerAdvice {

    private static final Set<ErrorCode> NOT_FOUND_CODES = new LinkedHashSet<>(Set.of(
            ErrorCode.ARTICLE_NOT_FOUND,
            ErrorCode.WITHOUT_ARTICLE,
            ErrorCode.USER_NOT_FOUND,
            ErrorCode.FOLLOWING_NOT_FOUND
    ));

    private static final Set<ErrorCode> UNAUTHORIZED_CODES = new LinkedHashSet<>(Set.of(
            ErrorCode.TOKEN_INVALID,
            ErrorCode.UNAUTHORIZED,
            ErrorCode.TOKEN_MISSING
    ));

    private static final Set<ErrorCode> FORBIDDEN_CODES = new LinkedHashSet<>(Set.of(
            ErrorCode.FORBIDDEN,
            ErrorCode.CANNOT_FOLLOW_SELF
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

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<?>> handleBizException(BizException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        log.error("{} occurred on {}: code={}, message={}",
                e.getClass().getSimpleName(),
                request.getRequestURI(),
                errorCode.getCode(),
                errorCode.getMessage(),
                e);
        return ResponseEntity.status(resolveStatus(errorCode))
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e, HttpServletRequest request) {
        log.error("{} occurred on {}: message={}",
                e.getClass().getSimpleName(),
                request.getRequestURI(),
                e.getMessage(),
                e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage()));
    }

    private HttpStatus resolveStatus(ErrorCode errorCode) {
        if (NOT_FOUND_CODES.contains(errorCode)) {
            return HttpStatus.NOT_FOUND;
        }
        if (UNAUTHORIZED_CODES.contains(errorCode)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (FORBIDDEN_CODES.contains(errorCode)) {
            return HttpStatus.FORBIDDEN;
        }
        if (errorCode == ErrorCode.INVALID_INPUT
                || errorCode == ErrorCode.OFFSET_MUST_BE_LARGGER_THAN_0
                || errorCode == ErrorCode.LIMITED_MUST_BE_LARGGER_THAN_0) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        return HttpStatus.OK;
    }
}
