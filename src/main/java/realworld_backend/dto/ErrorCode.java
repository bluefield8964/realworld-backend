package realworld_backend.dto;

public enum ErrorCode {

    UNKNOWN(5000,"UNKNOW"),

    UNAUTHORIZED(401, "Unauthorized"),
    USER_NOT_FOUND(1001, "User not found"),

    SYSTEM_ERROR(500, "System error"),

    FORBIDDEN(403, "Forbidden"),
    TOKEN_EXPIRED(401,"Token expired"),
    TOKEN_INVALID(401, "Invalid token"),
    TOKEN_MISSING(401, "Token missing");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
