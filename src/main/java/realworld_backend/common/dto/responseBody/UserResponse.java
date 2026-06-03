package realworld_backend.common.dto.responseBody;

import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.article.model.Role;
import realworld_backend.article.model.UserProfile;

import java.util.Set;

@NoArgsConstructor
@Data
public class UserResponse {
    private Long id;
    private String bio;
    private String image;
    private String username;
    private String email;
    private String Bearer;
    private Set<Role> roles;

    public UserResponse(UserProfile user, Set<Role> role) {
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.roles = role;
        this.bio = user.getBio();
        this.image = user.getImage();
    }

    public UserResponse(UserProfile user) {
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.bio = user.getBio();
        this.image = user.getImage();
    }
}

