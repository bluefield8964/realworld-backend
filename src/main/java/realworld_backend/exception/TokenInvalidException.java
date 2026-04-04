package realworld_backend.exception;

import realworld_backend.dto.ErrorCode;

public class TokenInvalidException extends RuntimeException{
    private final ErrorCode errorCode;

    public TokenInvalidException(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public ErrorCode geterrorCode() {
        return errorCode;
    }
}
