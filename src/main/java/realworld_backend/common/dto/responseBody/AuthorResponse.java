package realworld_backend.common.dto.responseBody;

import lombok.Data;
import realworld_backend.auth.model.User;

import java.util.Set;


@Data
public class AuthorResponse {


    private String username;
    private String bio;
    private String image;
    private boolean following;

    public static AuthorResponse from(User author, Set<Long> followingSet) {

        AuthorResponse dto = new AuthorResponse();

        dto.username = author.getUsername();
        dto.bio = author.getBio();
        dto.image = author.getImage();
        dto.following = followingSet.contains(author.getId());

        return dto;
    }

}

