package realworld_backend.commerce.model.exception;

import realworld_backend.common.exception.ErrorCode;

public class WebhookDuplicateIgnoredException extends RuntimeException{
    private final ErrorCode code;
    public WebhookDuplicateIgnoredException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode;
    }

    public ErrorCode getErrorCode() {
        return code;
    }
}