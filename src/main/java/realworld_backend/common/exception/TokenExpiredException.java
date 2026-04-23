package realworld_backend.common.exception;

public class TokenExpiredException extends RuntimeException {
    private final ErrorCode errorCode;

    public TokenExpiredException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

