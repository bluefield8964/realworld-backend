package realworld_backend.auth.domain.exception;

import realworld_backend.common.exception.ErrorCode;

public class AuthException extends RuntimeException {

    private final ErrorCode errorCode;

    public AuthException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public AuthException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getCode() {
        return errorCode.code();
    }

    @Override
    public String getMessage() {
        return super.getMessage(); // Keep RuntimeException message behavior
    }
}
