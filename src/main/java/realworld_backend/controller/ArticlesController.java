package realworld_backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import realworld_backend.dto.ArticleRequest;
import realworld_backend.dto.Error;
import realworld_backend.insolver.UserSolve;
import realworld_backend.model.Articles;
import realworld_backend.model.Author;
import realworld_backend.dto.ArticleResponse;
import realworld_backend.model.User;
import realworld_backend.repository.UserRepository;
import realworld_backend.service.ArticleService;
import realworld_backend.tool.TokenTool;

import java.time.LocalDateTime;
import java.util.Collections;

import java.util.HashMap;
import java.util.Optional;

public class ArticlesController {

    @Autowired
    private ArticleService articleService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TokenTool tokenTool;

    @PostMapping("/articles")
    public ResponseEntity<ArticleResponse> createArticlesByTags( @RequestBody ArticleRequest articlesResponse){
        Articles article = articlesResponse.getArticles();
        article.setSlug(generateSlug(article.getTitle()));
        article.setCreatedAt(LocalDateTime.now());
        article.setUpdatedAt(LocalDateTime.now());
        article.setFavorited(false);
        article.setFavoritesCount(0L);
        //principal was injected with email
        String email = (String)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Optional<User> byEmail = userRepository.findByEmail(email);
        User user = byEmail.get();
        // 2️⃣ 构造 author
        Author author = new Author();
        author.setUsername(user.getUsername());
        author.setAuthorId(user.getId());// 模拟用户
        article.setAuthor(author);
        ArticleResponse generateArticle = articleService.createArticle(article);
             return    ResponseEntity.status(HttpStatus.CREATED).body(generateArticle);

    }
    // slug 生成
    private String generateSlug(String title) {
        return title.toLowerCase().replace(" ", "-");
    }
}
