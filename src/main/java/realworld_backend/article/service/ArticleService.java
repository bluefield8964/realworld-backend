package realworld_backend.article.service;

import realworld_backend.common.time.UtcTimeMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.article.model.Article;
import realworld_backend.article.model.Author;
import realworld_backend.article.model.Tag;
import realworld_backend.article.model.UserProfile;
import realworld_backend.article.repository.ArticleRepository;
import realworld_backend.article.repository.AuthorRepository;
import realworld_backend.article.repository.CommentRepository;
import realworld_backend.article.repository.UserProfileRepository;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.commerce.service.subscription.SubscriptionAccessService;
import realworld_backend.common.dto.requestBody.ArticleRequest;
import realworld_backend.common.dto.responseBody.ArticleFeedResponse;
import realworld_backend.common.dto.responseBody.ArticleAccessResponse;
import realworld_backend.common.dto.responseBody.ArticleResponse;
import realworld_backend.common.dto.responseBody.AuthorResponse;
import realworld_backend.common.dto.responseBody.MultipleArticlesResponse;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleService {
    private final ArticleRepository articleReposity;
    private final FavoriteService favoriteService;
    private final TagService tagService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AuthorService authorService;
    private final FollowingService followingService;
    private final CommentRepository commentRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuthorRepository authorRepository;
    private final ObjectMapper objectMapper;
    private final SubscriptionAccessService subscriptionAccessService;

    public ArticleResponse createArticle(ArticleRequest articleRequest, CurrentAuthUser user) {
        //principal was injected user data
        Long userId = user.userId();
        if (userId == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        subscriptionAccessService.requireActiveSubscription(user);
        UserProfile currentProfile = loadCurrentUserProfile(userId);
        Author author = loadOrCreateAuthor(currentProfile);

        LocalDateTime now = UtcTimeMapper.nowUtc();
        ArticleRequest.ArticlePayload articleBody = articleRequest.getArticle();
        Set<Tag> tags = tagService.buildTags(articleBody.getTagList());
        Article article = Article.builder().title(articleBody.getTitle())
                .description(articleBody.getDescription())
                .body(articleBody.getBody())
                .createdAt(now)
                .updatedAt(now)
                .slug(generateSlug(articleBody.getTitle()))
                .favoritesCount(0L)
                .author(author)
                .tagList(tags)
                .title(articleBody.getTitle())
                .build();

        Article articlesSaved = articleReposity.save(article);

        return new ArticleResponse(articlesSaved, articlesSaved.getAuthor().getUsername());
    }


    public ArticleQueryResult getAllArticles(
            CurrentAuthUser currentUser,
            String authorName,
            String tag,
            String favoritedUsername,
            int limit,
            int offset
    ) {
        Author author = null;
        Set<Long> favoritedIds = Collections.emptySet();
        Set<Long> followingIds = Collections.emptySet();
        int safeLimit = limit <= 0 ? 20 : limit;
        int safeOffset = Math.max(offset, 0);
        Long favoritedUserId = null;

        if (authorName != null && !authorName.isBlank()) {
            author = authorService.findByUsername(authorName);
            if (author == null) {
                return new ArticleQueryResult(Collections.emptyList(), 0);
            }
        }

        if (favoritedUsername != null && !favoritedUsername.isBlank()) {
            UserProfile favoritedUser = userProfileRepository.findByUsername(favoritedUsername).orElse(null);
            if (favoritedUser == null) {
                return new ArticleQueryResult(Collections.emptyList(), 0);
            }
            favoritedUserId = favoritedUser.getId();
        }

        Pageable pageable = OffsetLimitPageRequest.of(
                safeOffset,
                safeLimit,
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );
        Page<Article> articlePage = articleReposity.findArticles(author, tag, favoritedUserId, pageable);
        List<Article> pageArticles = articlePage.getContent();
        int totalCount = articleReposity.countArticles(author, tag, favoritedUserId);


        if (currentUser != null && !pageArticles.isEmpty()) {
            favoritedIds = favoriteService.getFavoritedArticleIds(currentUser, pageArticles);
            followingIds = followingService.findFollowingIdsByFollowerId(currentUser);
        }

        Set<Long> finalFavoritedIds = favoritedIds;
        Set<Long> finalFollowingIds = followingIds;


        List<ArticleResponse> result = pageArticles.stream()
                .map(article -> {


                    AuthorResponse authorDto =
                            AuthorResponse.from(
                                    article.getAuthor().getUserProfile(),
                                    finalFollowingIds
                            );


                    ArticleResponse dto = ArticleResponse.from(article);

                    dto.setFavorited(
                            currentUser != null &&
                                    finalFavoritedIds.contains(article.getId())
                    );

                    dto.setAuthorResponse(authorDto);

                    return dto;
                })
                .toList();
        return new ArticleQueryResult(result, totalCount);
    }


    @Transactional
    public ArticleResponse updateArticle(String slug, JsonNode requestBody, CurrentAuthUser user) {
        if (requestBody == null || requestBody.isNull()) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }
        JsonNode articleNode = requestBody.get("article");
        if (articleNode == null || articleNode.isNull() || !articleNode.isObject()) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }
        if (articleNode.has("tagList") && articleNode.get("tagList").isNull()) {
            throw new BizException(ErrorCode.INVALID_INPUT);
        }

        ArticleRequest request = objectMapper.convertValue(requestBody, ArticleRequest.class);
        return updateArticle(slug, request, user);
    }

    @Transactional
    public ArticleResponse updateArticle(String slug, ArticleRequest request, CurrentAuthUser user) {
        if (user == null || user.userId() == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (request == null || request.getArticle() == null) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }

        Article article = articleReposity.findBySlug(slug)
                .orElseThrow(() -> new BizException(ErrorCode.ARTICLE_NOT_FOUND));
        if (!isOwnedByCurrentUser(article, user.userId())) {
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

        article.setUpdatedAt(UtcTimeMapper.nowUtc());
        Article save = articleReposity.save(article);

        ArticleResponse articleResponse = new ArticleResponse();
        articleResponse.setTitle(save.getTitle());
        articleResponse.setSlug(save.getSlug());
        articleResponse.setDescription(save.getDescription());
        List<String> list = save.getTagList().stream()
                .map(Tag::getName)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        articleResponse.setTagList(list);
        articleResponse.setCreatedAt(save.getCreatedAt());
        articleResponse.setUpdatedAt(save.getUpdatedAt());
        articleResponse.setFavoritesCount(save.getFavoritesCount());
        articleResponse.setBody(save.getBody());
        articleResponse.setAuthorResponse(AuthorResponse.from(save.getAuthor().getUserProfile(), Collections.emptySet()));
        articleResponse.setFavorited(favoriteService.checkFavorite(user, save));

        return articleResponse;
    }

    public ArticleResponse getArticleBySlug(String slug, CurrentAuthUser user) {
        Article article = articleReposity.findBySlug(slug).orElseThrow(() -> new BizException(ErrorCode.ARTICLE_NOT_FOUND));
        ArticleResponse articleResponse = new ArticleResponse();
        articleResponse.setTitle(article.getTitle());
        articleResponse.setSlug(article.getSlug());
        articleResponse.setDescription(article.getDescription());
        List<String> list = article.getTagList().stream()
                .map(Tag::getName)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        articleResponse.setTagList(list);
        articleResponse.setCreatedAt(article.getCreatedAt());
        articleResponse.setUpdatedAt(article.getUpdatedAt());
        articleResponse.setFavoritesCount(article.getFavoritesCount());
        Set<Long> followingIds = Collections.emptySet();
        boolean canReadBody = user != null && user.userId() != null && (
                isOwnedByCurrentUser(article, user.userId()) ||
                        subscriptionAccessService.hasActiveSubscription(user.userId(), UtcTimeMapper.nowUtc())
        );
        if (user != null) {
            followingIds = followingService.findFollowingIdsByFollowerId(user);
            articleResponse.setFavorited(favoriteService.checkFavorite(user, article));
        } else {
            articleResponse.setFavorited(false);
        }
        articleResponse.setAuthorResponse(AuthorResponse.from(article.getAuthor().getUserProfile(), followingIds));
        articleResponse.setBody(canReadBody ? article.getBody() : null);
        articleResponse.setAccess(ArticleAccessResponse.builder()
                .viewerState(user == null || user.userId() == null ? "UNAUTHENTICATED" : (canReadBody ? "FULL" : "LOCKED"))
                .canReadBody(canReadBody)
                .subscriptionRequired(true)
                .bundleCode("CREATOR_PRO_BUNDLE")
                .build());

        return articleResponse;
    }


    //feed messageFlow
    public MultipleArticlesResponse getFeed(CurrentAuthUser currentUser, int limit, int offset) {
        Set<Long> followingIds = followingService.findFollowingIdsByFollowerId(currentUser);

        if (followingIds.isEmpty()) {
            return new MultipleArticlesResponse(Collections.emptyList(), 0);
        }


        int totalCount = articleReposity
                .countByAuthorIdIn(followingIds);
        Pageable pageable = PageRequest.of(offset / limit, limit);


        List<Article> articles = articleReposity
                .findByAuthorIdInOrderByCreatedAtDesc(followingIds, pageable);
        Set<Long> followingSet = new HashSet<>(followingIds);
        Set<Long> favoritedIds = Collections.emptySet();
        if (!articles.isEmpty()) {
            favoritedIds = favoriteService.getFavoritedArticleIds(currentUser, articles);
        }
        Set<Long> finalFavoritedIds = favoritedIds;

        List<ArticleFeedResponse> result = articles.stream()
                .map(article -> {
                    ArticleFeedResponse dto = ArticleFeedResponse.from(article);
                    dto.setFavorited(finalFavoritedIds.contains(article.getId()));

                    AuthorResponse authorDto =
                            AuthorResponse.from(article.getAuthor().getUserProfile(), followingSet);

                    dto.setAuthor(authorDto);

                    return dto;
                })
                .toList();
        return new MultipleArticlesResponse(result, totalCount);
    }

    @Transactional
    public void deleteBySlug(String slug, CurrentAuthUser user) {
        Article article = articleReposity.findBySlug(slug)
                .orElseThrow(() -> new BizException(ErrorCode.ARTICLE_NOT_FOUND));
        if (!isOwnedByCurrentUser(article, user.userId())) {
            throw new BizException(ErrorCode.WITHOUT_ARTICLE);
        }
        commentRepository.deleteByArticle(article);
        articleReposity.delete(article);
    }

    @Transactional
    public ArticleResponse favoriteArticle(String slug, CurrentAuthUser user) {
        if (user == null || user.userId() == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        Article article = articleReposity.findBySlug(slug)
                .orElseThrow(() -> new BizException(ErrorCode.ARTICLE_NOT_FOUND));
        UserProfile userProfile = loadCurrentUserProfile(user.userId());
        favoriteService.favoriteArticle(userProfile, article);
        return getArticleBySlug(slug, user);
    }

    @Transactional
    public ArticleResponse unfavoriteArticle(String slug, CurrentAuthUser user) {
        if (user == null || user.userId() == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        Article article = articleReposity.findBySlug(slug)
                .orElseThrow(() -> new BizException(ErrorCode.ARTICLE_NOT_FOUND));
        UserProfile userProfile = loadCurrentUserProfile(user.userId());
        favoriteService.unfavoriteArticle(userProfile, article);
        return getArticleBySlug(slug, user);
    }

    // Build URL-friendly slug from title
    private String generateSlug(String title) {
        return title.toLowerCase().replace(" ", "-");
    }

    private UserProfile loadCurrentUserProfile(Long userId) {
        UserProfileCacheView cachedUser = (UserProfileCacheView) redisTemplate.opsForValue().get(UserProfileCacheView.key(userId));
        if (cachedUser != null) {
            return cachedUser.toEntity();
        }
        UserProfile userProfile = userProfileRepository.findByUserAuthId(userId)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        redisTemplate.opsForValue().set(UserProfileCacheView.key(userId), UserProfileCacheView.from(userProfile));
        return userProfile;
    }

    private Author loadOrCreateAuthor(UserProfile userProfile) {
        if (userProfile == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return authorRepository.findByUsername(userProfile.getUsername())
                .orElseGet(() -> authorRepository.save(Author.builder()
                        .username(userProfile.getUsername())
                        .userProfile(userProfile)
                        .build()));
    }

    private boolean isOwnedByCurrentUser(Article article, Long userAuthId) {
        if (article == null || userAuthId == null) {
            return false;
        }
        Author author = article.getAuthor();
        if (author == null || author.getUserProfile() == null) {
            return false;
        }
        Long authorUserAuthId = author.getUserProfile().getUserAuthId();
        return userAuthId.equals(authorUserAuthId);
    }

    public record ArticleQueryResult(List<ArticleResponse> articles, int totalCount) {
    }

}


