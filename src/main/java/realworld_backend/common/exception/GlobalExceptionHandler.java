package realworld_backend.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import realworld_backend.common.dto.responseBody.ApiResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<?>> handleBizException(BizException e) {
        log.error("BizException exception occurred: code={}, message={}",
                e.getErrorCode().getCode(),
                e.getErrorCode().getMessage());
        return ResponseEntity.ok(ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
    }
/*
* i need to relly on this exp to handle payment webhook system
*
* */

    //
    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ApiResponse<?>> handleTokenExpiredException(TokenExpiredException e) {
        log.error("TokenExpiredException exception occurred: code={}, message={}",
                e.getErrorCode().getCode(),
                e.getErrorCode().getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
    }

    @ExceptionHandler(TokenInvalidException.class)
    public ResponseEntity<ApiResponse<?>> handleTokenInvalidException(TokenInvalidException e) {
        log.error("TokenInvalidException exception occurred: code={}, message={}",
                e.getErrorCode().getCode(),
                e.getErrorCode().getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
    }


    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        log.error("TokenInvalidException exception occurred:  message={}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage()));
    }
}

