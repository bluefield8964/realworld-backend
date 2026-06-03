package realworld_backend.auth.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
/**
 * Shared request body for login and registration endpoints.
 */
public class LoginRequest {
    // Required only for login requests.
    @NotBlank(groups = LoginGroup.class, message = "identifier is required")
    private String identifier; // username or email
    // Required only for registration requests.
    @NotBlank(groups = RegisterGroup.class, message = "username is required")
    private String username;
    // Required only for registration requests.
    @NotBlank(groups = RegisterGroup.class, message = "email is required")
    @Email(groups = RegisterGroup.class, message = "email format invalid")
    private String email;
    // Required for both login and registration requests.
    @NotBlank(groups = {LoginGroup.class, RegisterGroup.class}, message = "password is required")
    private String password;

    private String deviceId;
    private Boolean rememberMe;
}
