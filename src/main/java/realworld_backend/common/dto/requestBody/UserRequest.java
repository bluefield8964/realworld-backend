package realworld_backend.common.dto.requestBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import realworld_backend.auth.model.Role;

import java.util.Set;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest {
    @JsonProperty("user")
    private UserAcceptor userAcceptor;
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAcceptor {
        // getter & setter
        @NotNull
        @Size(max = 50, message = "")
        private String username;
        @Email(message = "invalid email format")
        private String email;
        private String password;
        private Set<Role> roles;
        private String bio = null;
        private String image = null;
    }
}


