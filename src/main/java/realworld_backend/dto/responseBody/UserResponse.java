package realworld_backend.dto.responseBody;

import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.model.Role;
import realworld_backend.model.User;

import java.util.Set;

@NoArgsConstructor
@Data
public class UserResponse {
    private Long id;
    private String bio;
    private String image;
    private String username;
    private String email;
    //i follow the api command , but i prefer to set the Bearer  in the header
    private String Bearer ;
    private Set<Role> roles;
    public UserResponse(User user,Set<Role> role){
        this.username=user.getUsername();
        this.email=user.getEmail();
        //AVOID THE LAZY FETCH
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
