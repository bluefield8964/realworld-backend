package realworld_backend.dto.responseBody;

import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.model.accountModile.Role;
import realworld_backend.model.accountModile.User;

import java.util.Set;

@NoArgsConstructor
@Data
public class UserResponse {
    private Long id;
    private String bio;
    private String image;
    private String username;
    private String email;
    private String Bearer ;
    private Set<Role> roles;
    public UserResponse(User user,Set<Role> role){
        this.username=user.getUsername();
        this.email=user.getEmail();
        this.roles=role;
        this.bio=user.getBio();
        this.image=user.getImage();
    }
    public UserResponse(User user){
        this.username=user.getUsername();
        this.email=user.getEmail();
        this.bio=user.getBio();
        this.image=user.getImage();
    }
}
