package realworld_backend.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ErrorCode {
    // ===== 0. COMMON / UNKNOWN (5000~5099) =====
    UNKNOWN(5000, "Unknown error"),
    SYSTEM_ERROR(5001, "System error"),
    INVALID_INPUT(5002, "Invalid input"),
    // ===== 1. AUTH / SECURITY (1000~1599) =====

    // --- Basic authentication ---
    INVALID_CREDENTIALS(1000, "Username or password is incorrect"),
    INVALID_USERNAME(1001, "Invalid username format"),
    INVALID_EMAIL(1002, "Invalid email format"),
    INVALID_IDENTIFIER(1003, "Invalid identifier format"),
    INVALID_PASSWORD(7013, "invalid PASSWORD"),

    // --- Account status ---
    ACCOUNT_LOCKED(1100, "Account is locked"),
    ACCOUNT_DISABLED(1101, "Account is disabled"),
    ACCOUNT_BANNED(1102, "Account is banned"),

    // --- Login security ---
    TOO_MANY_ATTEMPTS(1200, "Too many login attempts"),

    // --- MFA ---
    MFA_REQUIRED(1300, "Multi-factor authentication required"),
    MFA_INVALID_CODE(1301, "Invalid MFA code"),
    MFA_CHALLENGE_EXPIRED(1302, "MFA challenge expired"),

    // --- Session ---
    SESSION_EXPIRED(1400, "Session expired"),
    SESSION_REVOKED(1401, "Session revoked"),
    SESSION_NOT_FOUND(1402, "Session not found"),

    // --- Token ---
    TOKEN_MISSING(1500, "Bearer token missing"),
    TOKEN_INVALID(1501, "Invalid token"),
    TOKEN_EXPIRED(1502, "Token expired"),
    REFRESH_TOKEN_INVALID(1503, "Invalid refresh token"),
    REFRESH_TOKEN_REUSED(1504, "Refresh token reuse detected"),

    // --- Authorization ---
    UNAUTHORIZED(1505, "Unauthorized"),
    FORBIDDEN(1506, "Forbidden"),

    // ===== 2. USER (2000~2099) =====
    USER_NOT_FOUND(2000, "User not found"),
    USER_ALREADY_REGISTERED(2001, "User already registered"),
    USER_JSON_ERROR(2002, "User JSON parse error"),
    FOLLOWING_NOT_FOUND(2003, "Following user not found"),
    CANNOT_FOLLOW_SELF(2004, "Cannot follow or unfollow yourself"),

    // ===== 3. ARTICLE (3000~3099) =====
    ARTICLE_NOT_FOUND(3000, "Article not found"),

    WITHOUT_ARTICLE(3001, "Article not found"),
    OFFSET_MUST_BE_LARGGER_THAN_0(3002, "offset must be >= 0"),
    LIMITED_MUST_BE_LARGGER_THAN_0(3003, "limit must be > 0"),
    // ===== 4. PAYMENT (7000~7099) =====

    // --- Order ---
    ORDER_NOT_FOUND(7000, "Order not found"),
    ORDER_ALREADY_CREATED(7001, "Order already created"),
    ORDER_FAILED(7002, "Order failed"),
    //--- Subscription ---
    SUBSCRIPTION_ALREADY_CREATED(7101, "subscription already created"),
    CUSTOMER_SUBSCRIPTION_NOT_FOUND(7102, "subscription not found"),
    SUBSCRIPTION_SESSION_NOT_FOUND(7103,"subscription session not found" ),
    SUBSCRIPTION_HISTORY_NOT_FOUND(7104, "subscription history not found"),
    SUBSCRIPTION_PLAN_NOT_FOUND(7105, "subscription plan not found"),

    // --- Payment ---
    PAYMENT_NOT_FOUND(7003, "Payment not found"),
    PAYMENT_URL_MISSING(7004, "Payment URL missing"),
    // --- Stripe ---
    STRIPE_SESSION_NOT_FOUND(7005, "Stripe session not found"),
    STRIPE_SESSION_CREATION_FAIL(7006, "Stripe session creation failed"),

    // ---- INVOICE ----
    INVOICE_SESSION_NOT_FOUND(7201,"invoice session not found" ),
    // --- Event / JSON ---
    JSON_ERROR(7007, "JSON error"),
    EVENT_PROCESSING(7008, "Event processing error"),

    STATEMENT_DOES_NOT_MATCH_EVENT_TYPE(7009, "statement doesn't match eventType"),
    EVENT_NOT_FOUND(7010,"event not found" ),
    // --- Lock ---
    LOCK_CANNOT_ACQUIRE(8101, "Lock cannot be acquired"),
    LOCK_INTERRUPTED(8110, "Lock interrupted"),

    // --- Idempotency ---
    IDEMPOTENCY_LOCK_FAILED(8211, "Idempotency lock failed"),

    // --- Retry ---
    RETRY_EXHAUSTED(8312, "Retry exhausted");

    private final int code;
    private final String message;


    public int code() {
        return code;
    }


    public String message() {
        return message;
    }

}

