package realworld_backend.dto.Exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ErrorCode {
    //UNCATEGORY
    UNKNOWN(5000, "UNKNOW"),

    //USER
    UNAUTHORIZED(401, "Unauthorized"),
    USER_NOT_FOUND(1001, "User not found"),
    USER_REGISTERED(1001, "User had registered"),
    USER_JSON_ERROR(1001, "Json have problem"),
    FOLLOWING_DOES_NOT_EXIT(1001, "Follower doesn't exit"),
    CAN_NOT_FOLLOW_OR_UNFOLLOW_YOURSELF(1001, "cant follow yourself"),
    //SYS
    SYSTEM_ERROR(500, "System error"),

    //AUTHENTICATION
    FORBIDDEN(403, "Forbidden"),
    TOKEN_EXPIRED(401, "Bearer expired"),
    TOKEN_INVALID(401, "Invalid token"),
    TOKEN_MISSING(401, "Bearer missing"),

    //ARTICLE
    WITHOUT_ARTICLE(4001, "Article not found"),

    //PAY
    PAYMENT_URL_MISSING(7000, "payment url missing "),
    ORDER_ALREADY_CREATED(7000, "payment already created"),
    STRIPE_SESSION_CREATION_FAIL(7000, "stripe session creation fail"),
    ORDER_NOT_FOUND(7000, "order not found"),
    TRIPE_SESSION_NOT_FOUND(7000, "stripe session not found"),
    PAYMENT_NOT_FOUND(7000,"PAYMENT NOT FOUND" ),
    JSON_ERROR(7000,"JSON ERROR" ), EVENT_PROCESSING(7000,"EVENT PROCESSING" );

    private final int code;
    private final String message;

}
