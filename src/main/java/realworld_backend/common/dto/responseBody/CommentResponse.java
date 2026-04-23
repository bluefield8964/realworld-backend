package realworld_backend.common.dto.responseBody;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.article.model.Comment;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentResponse {

    private Long id;
    private String body;
    private String createdAt;
    private String updatedAt;
    private AuthorResponse author;

    public static CommentResponse from(Comment comment, boolean following) {
        CommentResponse dto = new CommentResponse();

        dto.id = comment.getId();
        dto.body = comment.getBody();
        dto.createdAt = comment.getCreatedAt().toString();
        dto.updatedAt = comment.getUpdatedAt().toString();

        dto.author = AuthorResponse.from(
                comment.getAuthor(),
                following ? Set.of(comment.getAuthor().getId()) : Set.of()
        );

        return dto;
    }
}
