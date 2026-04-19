package realworld_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import realworld_backend.dto.Exception.BizException;
import realworld_backend.dto.Exception.ErrorCode;
import realworld_backend.dto.requestBody.ArticleRequest;
import realworld_backend.dto.responseBody.ApiResponse;
import realworld_backend.dto.responseBody.ArticleResponse;
import realworld_backend.dto.responseBody.MultipleArticlesResponse;
import realworld_backend.insolver.CurrentUser;
import realworld_backend.model.articleModule.Author;
import realworld_backend.model.accountModile.User;
import realworld_backend.service.ArticleService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api")
@RequiredArgsConstructor
public class ArticlesController {

    private final ObjectMapper objectMapper;
    private final ArticleService articleService;



    @PostMapping("/articles")
    public ApiResponse createArticlesByTags(@RequestBody ArticleRequest articleRequest,@AuthenticationPrincipal Jwt jwt) {

        ArticleResponse generateArticle = articleService.createArticle(articleRequest,jwt);
        return ApiResponse.success(generateArticle);
    }



    @GetMapping("/articles")
    public ApiResponse listArticles(@RequestParam(required = false) Author author,@CurrentUser User user,@RequestParam String tag) {

        List<ArticleResponse> articles = articleService.getAllArticles(user, author,tag);
        // return articles number and articles
        Map<String, Object> result = new HashMap<>();
        result.put("articles", articles);
        result.put("articlesCount", articles.size());
        return ApiResponse.success(result);
    }

    @PutMapping("/api/articles/{slug}")
    public ApiResponse updateArticle(@PathVariable String slug, @RequestBody JsonNode requestBody, @CurrentUser User user) {
        JsonNode articleNode = requestBody.get("article");
        if (articleNode.has("tagList") && articleNode.get("tagList").isNull()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        ArticleRequest request = objectMapper.convertValue(requestBody, ArticleRequest.class);
        ArticleResponse response = articleService.updateArticle(slug, request, user);
        return ApiResponse.success(response);}


    @GetMapping("/api/articles/{slug}")
    public ApiResponse getArticleBySlug(@PathVariable String slug,  @CurrentUser User user) {
        ArticleResponse response = articleService.getArticleBySlug(slug, user);
        return ApiResponse.success(response);}

    @GetMapping("/articles/feed")
    public ApiResponse getFeed(
            @CurrentUser User currentUser,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {

        if (currentUser == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        MultipleArticlesResponse response = articleService.getFeed(currentUser,limit,offset);
        return ApiResponse.success(response);
    }


    @DeleteMapping("/articles/{slug1}")
    public ResponseEntity deleteArticles(@PathVariable String slug,  @CurrentUser User user) {
        if (user == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        articleService.deleteBySlug(slug, user);
        return ResponseEntity.noContent().build();}
}
