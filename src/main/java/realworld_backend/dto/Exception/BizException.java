package realworld_backend.dto.Exception;

public class BizException extends RuntimeException{
    private final ErrorCode code;
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode;
    }

    public ErrorCode getErrorCode() {
        return code;
    }
}
