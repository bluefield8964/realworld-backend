package realworld_backend.dto.Exception;

public class TokenInvalidException extends RuntimeException{
    private final ErrorCode errorCode;

    public TokenInvalidException(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
