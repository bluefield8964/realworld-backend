package realworld_backend.commerce.controller;

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
import realworld_backend.commerce.model.log.WebhookDecisionException;
import realworld_backend.commerce.service.core.PaymentChannelException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice(basePackages = "realworld_backend.commerce.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CommerceControllerAdvice {

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

    @ExceptionHandler(PaymentChannelException.class)
    public ResponseEntity<ApiResponse<?>> handlePaymentChannelException(
            PaymentChannelException e,
            HttpServletRequest request
    ) {
        log.error("{} occurred on {}: provider={}, providerCode={}, requestId={}, retryable={}, message={}",
                e.getClass().getSimpleName(),
                request.getRequestURI(),
                e.getProvider(),
                e.getErrorCode(),
                e.getRequestId(),
                e.isRetryable(),
                e.getMessage(),
                e);
        return ResponseEntity.ok(ApiResponse.error(
                ErrorCode.PAYMENT_STATUS_CHANGE_FAIL.getCode(),
                e.getMessage()
        ));
    }

    @ExceptionHandler(WebhookDecisionException.class)
    public ResponseEntity<ApiResponse<?>> handleWebhookDecisionException(
            WebhookDecisionException e,
            HttpServletRequest request
    ) {
        log.error("{} occurred on {}: decision={}, message={}",
                e.getClass().getSimpleName(),
                request.getRequestURI(),
                e.getDecision(),
                e.getMessage(),
                e);
        return ResponseEntity.ok(ApiResponse.error(
                ErrorCode.EVENT_PROCESSING.getCode(),
                e.getMessage()
        ));
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
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage()));
    }
}
