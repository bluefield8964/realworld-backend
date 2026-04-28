package realworld_backend.article.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import realworld_backend.article.service.ArticleService;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.common.dto.requestBody.ArticleRequest;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.dto.responseBody.ArticleResponse;
import realworld_backend.common.dto.responseBody.MultipleArticlesResponse;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.common.web.resolver.CurrentUser;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ArticlesController {

    private final ArticleService articleService;


    @PostMapping("/articles")
    public ResponseEntity<ApiResponse> createArticlesByTags(
            @RequestBody ArticleRequest articleRequest,
            @CurrentUser(required = true) CurrentAuthUser user
    ) {

        ArticleResponse generateArticle = articleService.createArticle(articleRequest, user);
        Map<String, Object> data = new HashMap<>();
        data.put("article", generateArticle);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }


    @GetMapping("/articles")
    public ApiResponse listArticles(
            @RequestParam(required = false) String author,
            @CurrentUser CurrentAuthUser user,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String favorited,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {

        ArticleService.ArticleQueryResult queryResult =
                articleService.getAllArticles(user, author, tag, favorited, limit, offset);
        // return articles number and articles
        Map<String, Object> result = new HashMap<>();
        result.put("articles", queryResult.articles());
        result.put("articlesCount", queryResult.totalCount());
        return ApiResponse.success(result);
    }


    @GetMapping("/articles/{slug}")
    public ResponseEntity<ApiResponse<?>> getArticleBySlug(
            @PathVariable String slug,
            @CurrentUser(required = false) CurrentAuthUser user
    ) {
        try {
            ArticleResponse response = articleService.getArticleBySlug(slug, user);
            Map<String, Object> data = new HashMap<>();
            data.put("article", response);
            return ResponseEntity.ok(ApiResponse.success(data));
        } catch (BizException e) {
            if (e.getErrorCode() == ErrorCode.ARTICLE_NOT_FOUND || e.getErrorCode() == ErrorCode.WITHOUT_ARTICLE) {
                ApiResponse<?> body = new ApiResponse<>(
                        e.getErrorCode().getCode(),
                        e.getErrorCode().getMessage(),
                        Map.of("errors", Map.of("article", List.of("not found")))
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            throw e;
        }
    }


    @PutMapping("/articles/{slug}")
    public ResponseEntity<ApiResponse> updateArticle(
            @PathVariable String slug,
            @RequestBody JsonNode requestBody,
            @CurrentUser(required = true) CurrentAuthUser user
    ) {
        ArticleResponse response;
        try {
            response = articleService.updateArticle(slug, requestBody, user);
        } catch (BizException e) {
            if (e.getErrorCode() == ErrorCode.JSON_ERROR || e.getErrorCode() == ErrorCode.INVALID_INPUT) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(ApiResponse.error(e.getErrorCode().getCode(), e.getErrorCode().getMessage()));
            }
            throw e;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("article", response);
        return ResponseEntity.ok(ApiResponse.success(data));
    }


    @GetMapping("/articles/feed")
    public ApiResponse getFeed(
            @CurrentUser(required = true) CurrentAuthUser currentUser,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {

        if (currentUser == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        MultipleArticlesResponse response = articleService.getFeed(currentUser, limit, offset);
        return ApiResponse.success(response);
    }


    @DeleteMapping("/articles/{slug}")
    public ResponseEntity deleteArticles(@PathVariable String slug, @CurrentUser(required = true) CurrentAuthUser user) {
        if (user == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        articleService.deleteBySlug(slug, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/articles/{slug}/favorite")
    public ResponseEntity<ApiResponse<?>> favoriteArticle(
            @PathVariable String slug,
            @CurrentUser(required = true) CurrentAuthUser user
    ) {
        ArticleResponse response = articleService.favoriteArticle(slug, user);
        Map<String, Object> data = new HashMap<>();
        data.put("article", response);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @DeleteMapping("/articles/{slug}/favorite")
    public ResponseEntity<ApiResponse<?>> unfavoriteArticle(
            @PathVariable String slug,
            @CurrentUser(required = true) CurrentAuthUser user
    ) {
        ArticleResponse response = articleService.unfavoriteArticle(slug, user);
        Map<String, Object> data = new HashMap<>();
        data.put("article", response);
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}

