package realworld_backend.common.dto.requestBody;

import lombok.Data;

@Data
public class CreateCommentRequest {
    private CommentData comment;


    @Data
    public static class CommentData {
        private String body;


    }
}

