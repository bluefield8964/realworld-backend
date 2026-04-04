package realworld_backend.dto.Exception;

import realworld_backend.dto.ErrorCode;

public class BizException extends RuntimeException{
    private final int code;
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
