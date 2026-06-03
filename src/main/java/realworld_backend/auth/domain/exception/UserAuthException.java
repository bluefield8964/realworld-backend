package realworld_backend.auth.domain.exception;

import realworld_backend.common.exception.ErrorCode;

public class UserAuthException extends AuthException{

    public UserAuthException(ErrorCode code) {
        super(code);
    }
}
