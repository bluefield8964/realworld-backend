package realworld_backend.common.dto.requestBody;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Set;



@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * Request body wrapper for article create and update endpoints.
 */
public class ArticleRequest  {
    @JsonProperty("article")
    // Preserve the RealWorld payload shape: { "article": { ... } }.
    private ArticlePayload article;

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    /**
     * Inner payload that carries mutable article fields.
     */
    public static class ArticlePayload {
        private String title;
        private String description;
        private String slug;
        private String body;
        // Tag names attached to the article payload.
        private Set<String> tagList;
    }
}

