package realworld_backend.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.dto.Exception.BizException;
import realworld_backend.dto.Exception.ErrorCode;
import realworld_backend.dto.requestBody.ArticleRequest;
import realworld_backend.dto.responseBody.ArticleFeedResponse;
import realworld_backend.dto.responseBody.ArticleResponse;
import realworld_backend.dto.responseBody.AuthorResponse;
import realworld_backend.dto.responseBody.MultipleArticlesResponse;
import realworld_backend.model.Article;
import realworld_backend.model.Author;
import realworld_backend.model.Tag;
import realworld_backend.model.User;
import realworld_backend.repository.ArticleRepository;
import realworld_backend.repository.FollowRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ArticleService {
    private final ArticleRepository articleReposity;
    private final FavoriteService favoriteService;
    private final TagService tagService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final FollowRepository followRepository;

    public ArticleService(ArticleRepository articleReposity, FavoriteService favoriteService, TagService tagService, FollowRepository followRepository, RedisTemplate<String, Object> redisTemplate) {
        this.articleReposity = articleReposity;
        this.favoriteService = favoriteService;
        this.tagService = tagService;
        this.followRepository = followRepository;
        this.redisTemplate = redisTemplate;
    }

    public ArticleResponse createArticle(ArticleRequest articleRequest, Jwt jwt) {
        Article article = new Article();
        ArticleRequest.articleAcceptor articleBody = articleRequest.getArticle();
        article.setSlug(generateSlug(articleBody.getTitle()));
        article.setTitle(articleBody.getTitle());
        article.setDescription(articleBody.getDescription());
        article.setBody(articleBody.getBody());
        Set<Tag> tags = tagService.buildTags(articleBody.getTagList());
        article.setTagList(tags);
        article.setCreatedAt(LocalDateTime.now());
        article.setUpdatedAt(LocalDateTime.now());
        article.setFavoritesCount(0L);

        //principal was injected user data
        String username = jwt.getClaim("username");
        Long userId = jwt.getClaim("userId");
        // construct author
        User redisUser = (User) redisTemplate.opsForValue().get("user:" + userId);
        Author author = new Author();
        assert redisUser != null;
        author.setUsername(redisUser.getUsername());
        author.setId(redisUser.getId());
        author.setUser(redisUser);
        article.setAuthor(author);

        Article articlesSaved = articleReposity.save(article);

        return new ArticleResponse(articlesSaved, articlesSaved.getAuthor().getUsername());
    }

    public List<ArticleResponse> getAllArticles(User currentUser, Author author, String tag) {
        List<Article> articles = articleReposity
                .findArticles(author, tag);
        Set<Long> favoritedIds = Collections.emptySet();
        if (currentUser != null && !articles.isEmpty()) {
            List<Long> articleIds = articles.stream()
                    .map(Article::getId)
                    .toList();

            favoritedIds = favoriteService
                    .getFavoritedArticleIds(currentUser, articleIds);
        }
        Set<Long> followingIds = Collections.emptySet();

        if (currentUser != null) {
            followingIds = new HashSet<>(
                    followRepository.findFollowingIdsByFollowerId(currentUser.getId())
            );
        }
        Set<Long> finalFavoritedIds = favoritedIds;
        Set<Long> finalFollowingIds = followingIds;


        return articles.stream()
                .map(article -> {

                    // ⭐ author DTO
                    AuthorResponse authorDto =
                            AuthorResponse.from(
                                    article.getAuthor().getUser(),
                                    finalFollowingIds
                            );

                    // ⭐ article DTO
                    ArticleResponse dto = ArticleResponse.from(article);

                    dto.setFavorited(
                            currentUser != null &&
                                    finalFavoritedIds.contains(article.getId())
                    );

                    dto.setAuthorResponse(authorDto);

                    return dto;
                })
                .toList();
    }

    @Transactional
    public ArticleResponse updateArticle(String slug, ArticleRequest request, User user) {
        Article article = articleReposity.findBySlug(slug)
                .orElseThrow(() -> new BizException(ErrorCode.WITHOUT_ARTICLE));
        // checking authentication
        if (!article.getAuthor().getId().equals(user.getId())) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (request.getArticle().getBody() != null) {
            article.setBody(request.getArticle().getBody());

        }
        if (request.getArticle().getTagList() != null) {
            //extract tag from articleRequest
            Set<String> tagList = request.getArticle().getTagList();
            Set<Tag> tags = tagService.buildTags(tagList);
            article.setTagList(tags);

        }
        article.setUpdatedAt(LocalDateTime.now());

        Article save = articleReposity.save(article);
        ArticleResponse articleResponse = new ArticleResponse();
        articleResponse.setTitle(save.getTitle());
        articleResponse.setSlug(save.getSlug());
        articleResponse.setDescription(save.getDescription());
        Set<String> list = save.getTagList().stream().map(Tag::getName).collect(Collectors.toSet());
        articleResponse.setTagList(list);
        articleResponse.setCreatedAt(save.getCreatedAt());
        articleResponse.setUpdatedAt(save.getUpdatedAt());
        articleResponse.setFavoritesCount(save.getFavoritesCount());
        articleResponse.setFavorited(favoriteService.checkFavorite(user, save));

        return articleResponse;
    }

    public ArticleResponse getArticleBySlug(String slug, User user) {
        Article article = articleReposity.findBySlug(slug).orElseThrow(() -> new BizException(ErrorCode.WITHOUT_ARTICLE));
        ArticleResponse articleResponse = new ArticleResponse();
        articleResponse.setTitle(article.getTitle());
        articleResponse.setSlug(article.getSlug());
        articleResponse.setDescription(article.getDescription());
        Set<String> list = article.getTagList().stream().map(Tag::getName).collect(Collectors.toSet());
        articleResponse.setTagList(list);
        articleResponse.setCreatedAt(article.getCreatedAt());
        articleResponse.setUpdatedAt(article.getUpdatedAt());
        articleResponse.setFavoritesCount(article.getFavoritesCount());
        if (user != null) {
            articleResponse.setFavorited(favoriteService.checkFavorite(user, article));
        } else {
            articleResponse.setFavorited(false);
        }
        return articleResponse;
    }


    //feed messageFlow
    public MultipleArticlesResponse getFeed(User currentUser, int limit, int offset) {
        List<Long> followingIds = followRepository
                .findFollowingIdsByFollowerId(currentUser.getId());

        if (followingIds.isEmpty()) {
            return new MultipleArticlesResponse(Collections.emptyList(), 0);
        }


        int totalCount = articleReposity
                .countByAuthorIdIn(followingIds);
        Pageable pageable = PageRequest.of(offset / limit, limit);


        List<Article> articles = articleReposity
                .findByAuthorIdInOrderByCreatedAtDesc(followingIds, pageable);
        Set<Long> followingSet = new HashSet<>(followingIds);

        List<ArticleFeedResponse> result = articles.stream()
                .map(article -> {
                    ArticleFeedResponse dto = ArticleFeedResponse.from(article);

                    AuthorResponse authorDto =
                            AuthorResponse.from(article.getAuthor().getUser(), followingSet);

                    dto.setAuthor(authorDto);

                    return dto;
                })
                .toList();
        return new MultipleArticlesResponse(result, totalCount);
    }

    public void deleteBySlug(String slug, User user) {
        Article article = articleReposity.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Article not found"));
        if (!article.getAuthor().getUser().getId().equals(user.getId())) {
            throw new BizException(ErrorCode.WITHOUT_ARTICLE);
        }
        articleReposity.delete(article);
    }

    // slug 生成
    private String generateSlug(String title) {
        return title.toLowerCase().replace(" ", "-");
    }


}
