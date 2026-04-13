package realworld_backend.dto.responseBody;

import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.model.Article;
import realworld_backend.model.Tag;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@NoArgsConstructor
@Data
public class ArticleResponse {

    public ArticleResponse(Article article, String authorName) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.description = article.getDescription();
        this.body = article.getBody();

        this.tagList = article.getTagList().stream().map(Tag::getName).collect(Collectors.toSet());
        this.authorResponse.setUsername(authorName);
        this.slug = article.getSlug();
        this.createdAt = article.getCreatedAt();
        this.favoritesCount = article.getFavoritesCount();
    }

    public ArticleResponse(Article article) {
        this.id = article.getId();
        this.title = article.getTitle();
        this.description = article.getDescription();
        this.body = article.getBody();
        this.tagList = article.getTagList().stream().map(Tag::getName).collect(Collectors.toSet());
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
        dto.tagList = article.getTagList().stream().map(Tag::getName).collect(Collectors.toSet());

        dto.createdAt = article.getCreatedAt();
        dto.updatedAt = article.getUpdatedAt();

        dto.favoritesCount = article.getFavoritesCount();

        return dto;
    }

    private Long id;  // 主键

    private String title;

    private String description;

    private String body;

    private Set<String> tagList;

    private AuthorResponse authorResponse;

    private String slug;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean favorited;
    private Long favoritesCount;

}
