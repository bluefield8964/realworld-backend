package realworld_backend.tool;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import realworld_backend.dto.ApiResponse;
import realworld_backend.dto.Exception.BizException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<?> handleBizException(BizException e){
        return ApiResponse.error(e.getCode(), e.getMessage());
    }
    @ExceptionHandler
    public ApiResponse<?> handleException(Exception e){
        return ApiResponse.error(500,"internal server error");  }
}
