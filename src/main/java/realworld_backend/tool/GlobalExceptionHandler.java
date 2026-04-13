package realworld_backend.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import realworld_backend.dto.Exception.BizException;
import realworld_backend.dto.Exception.ErrorCode;
import realworld_backend.dto.Exception.TokenExpiredException;
import realworld_backend.dto.Exception.TokenInvalidException;
import realworld_backend.dto.responseBody.ApiResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<?> handleBizException(BizException e) {
        log.error("BizException exception occurred: code={}, message={}",
                e.getErrorCode().getCode(),
                e.getErrorCode().getMessage());
        return ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage());
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ApiResponse<?> handleTokenExpiredException(TokenExpiredException e) {
        log.error("TokenExpiredException exception occurred: code={}, message={}",
                e.getErrorCode().getCode(),
                e.getErrorCode().getMessage());
        return ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage());
    }

    @ExceptionHandler(TokenInvalidException.class)
    public ApiResponse<?> handleTokenInvalidException(TokenInvalidException e) {
        log.error("TokenInvalidException exception occurred: code={}, message={}",
                e.getErrorCode().getCode(),
                e.getErrorCode().getMessage());
        return ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage());
    }


    @ExceptionHandler
    public ApiResponse<?> handleException(Exception e) {
        log.error("TokenInvalidException exception occurred:  message={}", e.getMessage());
        return ApiResponse.error(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage());
    }
}
