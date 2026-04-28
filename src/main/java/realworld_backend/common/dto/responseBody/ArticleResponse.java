package realworld_backend.common.dto.responseBody;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import realworld_backend.article.model.Article;
import realworld_backend.article.model.Tag;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@NoArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArticleResponse {

    public ArticleResponse(Article article, String authorName) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.description = article.getDescription();
        this.body = article.getBody();

        this.tagList = mapTagNames(article);
        this.authorResponse = new AuthorResponse();
        this.authorResponse.setUsername(authorName);
        this.slug = article.getSlug();
        this.createdAt = article.getCreatedAt();
        this.updatedAt = article.getUpdatedAt();
        this.favoritesCount = article.getFavoritesCount();
        this.favorited = false;
    }

    public ArticleResponse(Article article) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.description = article.getDescription();
        this.body = article.getBody();
        this.tagList = mapTagNames(article);
        this.slug = article.getSlug();
        this.createdAt = article.getCreatedAt();
        this.updatedAt = article.getUpdatedAt();
        this.favoritesCount = article.getFavoritesCount();
    }

    public static ArticleResponse from(Article article) {
        ArticleResponse dto = new ArticleResponse();

        dto.slug = article.getSlug();
        dto.title = article.getTitle();
        dto.description = article.getDescription();
        dto.tagList = mapTagNames(article);

        dto.createdAt = article.getCreatedAt();
        dto.updatedAt = article.getUpdatedAt();

        dto.favoritesCount = article.getFavoritesCount();

        return dto;
    }

    private Long id;

    private String title;

    private String description;

    private String body;

    private List<String> tagList;

    @JsonProperty("author")
    private AuthorResponse authorResponse;

    private String slug;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean favorited;
    private Long favoritesCount;

    private static List<String> mapTagNames(Article article) {
        return article.getTagList().stream()
                .map(Tag::getName)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

}

