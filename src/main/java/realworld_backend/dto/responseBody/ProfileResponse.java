package realworld_backend.dto.responseBody;

import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.model.User;

@NoArgsConstructor
@Data
public class ProfileResponse {
    private String username;
    private String bio;
    private String image;
    private Boolean following;

    public ProfileResponse(User follower) {
        this.username = follower.getUsername();
        this.bio = follower.getBio();
        this.image = follower.getImage();
    }


}
