package realworld_backend.dto.requestBody;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Set;



@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleRequest  {
    @JsonProperty("article")
    // DTO getters & setters
    private articleAcceptor article;

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class articleAcceptor {
        private String title;

        private String description;

        private String slug;

        private String body;
        // taglist cant be null
        private Set<String> tagList;
    }


}
