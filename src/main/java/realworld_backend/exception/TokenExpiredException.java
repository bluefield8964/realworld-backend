package realworld_backend.exception;

import org.springframework.security.core.AuthenticationException;
import realworld_backend.dto.ErrorCode;

public class TokenExpiredException extends RuntimeException {
    private final ErrorCode errorCode;

    public TokenExpiredException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode geterrorCode() {
        return errorCode;
    }
}
