package realworld_backend.common.dto.responseBody;

import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.article.model.UserProfile;

@NoArgsConstructor
@Data
public class ProfileResponse {
    private String username;
    private String bio;
    private String image;
    private Boolean following;

    public ProfileResponse(UserProfile follower) {
        this.username = follower.getUsername();
        this.bio = follower.getBio();
        this.image = follower.getImage();
    }


}

