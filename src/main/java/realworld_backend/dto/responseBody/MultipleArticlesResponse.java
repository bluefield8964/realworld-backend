package realworld_backend.dto.responseBody;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Data
public class MultipleArticlesResponse {
    private List<ArticleFeedResponse> articles;
    private int articlesCount;

}
