package realworld_backend.common.exception;

public class TokenInvalidException extends RuntimeException{
    private final ErrorCode errorCode;

    public TokenInvalidException(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

