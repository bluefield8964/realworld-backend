package realworld_backend.dto.responseBody;

import lombok.NoArgsConstructor;
import realworld_backend.model.articleModule.Article;
import realworld_backend.model.articleModule.Tag;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@NoArgsConstructor

public class ArticleFeedResponse {

    private String slug;
    private String title;
    private String description;
    private Set<String> tagList;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean favorited;
    private Long favoritesCount;
    private AuthorResponse author;

    public static ArticleFeedResponse from(Article article) {
        ArticleFeedResponse dto = new ArticleFeedResponse();

        dto.slug = article.getSlug();
        dto.title = article.getTitle();
        dto.description = article.getDescription();
        dto.tagList = article.getTagList().stream().map(Tag::getName).collect(Collectors.toSet());
        dto.createdAt = article.getCreatedAt();
        dto.updatedAt = article.getUpdatedAt();

        dto.favorited = false;
        dto.favoritesCount = 0L;

        return dto;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Set<String> getTagList() {
        return tagList;
    }

    public void setTagList(Set<String> tagList) {
        this.tagList = tagList;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getFavoritesCount() {
        return favoritesCount;
    }

    public void setFavoritesCount(Long favoritesCount) {
        this.favoritesCount = favoritesCount;
    }

    public boolean isFavorited() {
        return favorited;
    }

    public void setFavorited(boolean favorited) {
        this.favorited = favorited;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }


    public void setAuthor(AuthorResponse author) {
        this.author = author;
    }

    public AuthorResponse getAuthor() {
        return author;
    }


}
