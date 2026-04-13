package realworld_backend.dto.requestBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import realworld_backend.model.Role;

import java.util.Set;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest {
    @JsonProperty("user")
    private UserAcceptor userAcceptor;
    // 业务数据嵌套类
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAcceptor {
        // getter & setter
        @NotNull
        @Size(max = 50, message = "用户名长度不能超过50")
        private String username;
        @Email(message = "邮箱格式不正确")
        private String email;
        private String password;
        private Set<Role> roles;
        private String bio = null;
        private String image = null;
    }
}
