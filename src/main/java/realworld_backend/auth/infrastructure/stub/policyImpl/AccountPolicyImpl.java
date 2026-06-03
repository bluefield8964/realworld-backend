package realworld_backend.auth.infrastructure.stub.policyImpl;

import org.springframework.stereotype.Service;
import realworld_backend.auth.domain.exception.UserAuthException;
import realworld_backend.auth.domain.service.policy.AccountPolicy;
import realworld_backend.common.exception.ErrorCode;

import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AccountPolicyImpl implements AccountPolicy {

    private static final int MIN_LENGTH = 10;
    private static final int MAX_LENGTH = 15;

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+=.-]{8,64}$");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private static final Pattern ALLOWED_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._-]+$");

    private static final Pattern PURE_NUMBER_PATTERN =
            Pattern.compile("^\\d+$");

    private static final Pattern CONSECUTIVE_SYMBOL_PATTERN =
            Pattern.compile(".*[._-]{2,}.*");

    private static final Set<String> RESERVED_USERNAMES = Set.of(
            "admin",
            "administrator",
            "root",
            "system",
            "support",
            "help",
            "null",
            "undefined",
            "test",
            "guest",
            "owner",
            "security",
            "official"
    );


    @Override
    public Boolean validatePassword(String password) throws UserAuthException {
        return validatePasswordForm(password);
    }

    /**
     * Validates the given username based on predefined rules.
     *
     * @param username the username to validate
     * @throws IllegalArgumentException if validation fails
     */
    @Override
    public Boolean validateUsername(String username) throws UserAuthException {
        if (username == null || username.isBlank()) {
            throw new UserAuthException(ErrorCode.INVALID_USERNAME);
        }

        String normalized = username.trim();

        // Length check
        if (normalized.length() < MIN_LENGTH || normalized.length() > MAX_LENGTH) {
            throw new UserAuthException(ErrorCode.INVALID_USERNAME);
        }

        // Prevent mixing username with email format
        if (normalized.contains("@")) {
            throw new UserAuthException(ErrorCode.INVALID_USERNAME);
        }

        // Allowed characters check
        if (!ALLOWED_PATTERN.matcher(normalized).matches()) {
            throw new UserAuthException(ErrorCode.INVALID_USERNAME);
        }

        // Prevent starting or ending with special symbols
        if (startsOrEndsWithSymbol(normalized)) {
            throw new UserAuthException(ErrorCode.INVALID_USERNAME);
        }

        // Prevent repeated symbols
        if (CONSECUTIVE_SYMBOL_PATTERN.matcher(normalized).matches()) {
            throw new UserAuthException(ErrorCode.INVALID_USERNAME);
        }

        // Prevent purely numeric usernames
        if (PURE_NUMBER_PATTERN.matcher(normalized).matches()) {
            throw new UserAuthException(ErrorCode.INVALID_USERNAME);
        }

        // Reserved keyword check (case-insensitive)
        if (RESERVED_USERNAMES.contains(normalized.toLowerCase())) {
            throw new UserAuthException(ErrorCode.INVALID_USERNAME);
        }
        return true;
    }

    /**
     * Checks whether the username starts or ends with a special symbol.
     *
     * @param username the username string
     * @return true if it starts or ends with '.', '_' or '-'
     */
    private boolean startsOrEndsWithSymbol(String username) {
        return username.startsWith(".")
                || username.startsWith("_")
                || username.startsWith("-")
                || username.endsWith(".")
                || username.endsWith("_")
                || username.endsWith("-");
    }

    /**
     * Validates the given email based on predefined rules.
     *
     * @param email the email to validate
     * @return true if valid, false otherwise
     */
    @Override
    public Boolean validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new UserAuthException(ErrorCode.INVALID_EMAIL);
        }

        String normalized = email.trim().toLowerCase();

        // Length check (common max length for email is 32)
        if (normalized.length() < 8 || normalized.length() > 32) {
            throw new UserAuthException(ErrorCode.INVALID_EMAIL);
        }

        // Basic structure check: must contain exactly one '@'
        int atIndex = normalized.indexOf('@');
        if (atIndex <= 0 || atIndex != normalized.lastIndexOf('@')) {
            throw new UserAuthException(ErrorCode.INVALID_EMAIL);
        }

        String localPart = normalized.substring(0, atIndex);
        String domainPart = normalized.substring(atIndex + 1);

        // Local part length check
        if (localPart.length() > 24) {
            throw new UserAuthException(ErrorCode.INVALID_EMAIL);
        }

        // Domain part must contain at least one dot
        if (!domainPart.contains(".")) {
            throw new UserAuthException(ErrorCode.INVALID_EMAIL);
        }

        // Prevent starting or ending with dot
        if (normalized.startsWith(".") || normalized.endsWith(".")) {
            throw new UserAuthException(ErrorCode.INVALID_EMAIL);
        }

        // Prevent consecutive dots
        if (normalized.contains("..")) {
            throw new UserAuthException(ErrorCode.INVALID_EMAIL);
        }

        // Allowed characters check (simple version)
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new UserAuthException(ErrorCode.INVALID_EMAIL);
        }

        return true;
    }

    public boolean validatePasswordForm(String password) throws UserAuthException {

        if (password == null || password.isBlank()) {
            throw new UserAuthException(ErrorCode.INVALID_PASSWORD);
        }

        String normalized = password.trim();

        // Length check
        if (normalized.length() < MIN_LENGTH || normalized.length() > MAX_LENGTH) {
            throw new UserAuthException(ErrorCode.INVALID_PASSWORD);
        }

        // Spaces are not allowed
        if (normalized.contains(" ")) {
            throw new UserAuthException(ErrorCode.INVALID_PASSWORD);
        }

        // Regex rule validation
        if (!PASSWORD_PATTERN.matcher(normalized).matches()) {
            throw new UserAuthException(ErrorCode.INVALID_PASSWORD);
        }

        return true;
    }
}
