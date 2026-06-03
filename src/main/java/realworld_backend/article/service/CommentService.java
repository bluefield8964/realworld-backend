package realworld_backend.article.service;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.article.model.Article;
import realworld_backend.article.model.Comment;
import realworld_backend.article.repository.ArticleRepository;
import realworld_backend.article.repository.CommentRepository;
import realworld_backend.article.repository.FollowRepository;
import realworld_backend.article.repository.UserProfileRepository;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.common.dto.requestBody.CreateCommentRequest;
import realworld_backend.common.dto.responseBody.AuthorResponse;
import realworld_backend.common.dto.responseBody.CommentResponse;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final ArticleRepository articleReposity;
    private final CommentRepository commentRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final FollowRepository followRepository;
    private final UserProfileRepository userProfileRepository;

    public CommentResponse createComment(
            String slug,
            CreateCommentRequest request,
            CurrentAuthUser currentUser
    ) {
        if (currentUser == null || currentUser.userId() == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (request == null || request.getComment() == null
                || request.getComment().getBody() == null
                || request.getComment().getBody().isBlank()) {
            throw new BizException(ErrorCode.INVALID_INPUT);
        }

        Article article = articleReposity.findBySlug(slug)
                .orElseThrow(() -> new BizException(ErrorCode.WITHOUT_ARTICLE));
        realworld_backend.article.model.UserProfile currentProfile = userProfileRepository.findByUserAuthId(currentUser.userId())
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));

        Comment comment = new Comment();
        comment.setBody(request.getComment().getBody());
        comment.setUserProfile(currentProfile);
        comment.setArticle(article);
        comment.setCreatedAt(UtcTimeMapper.nowUtc());
        comment.setUpdatedAt(UtcTimeMapper.nowUtc());

        commentRepository.save(comment);

        return CommentResponse.from(comment, false);
    }


    public List<CommentResponse> getComments(String slug, CurrentAuthUser currentUser) {


        Article article = articleReposity.findBySlug(slug)
                .orElseThrow(() -> new BizException(ErrorCode.WITHOUT_ARTICLE));

        List<Comment> comments =
                commentRepository.findByArticleOrderByCreatedAtDesc(article);

        Set<Long> followingIds = Collections.emptySet();

        if (currentUser != null) {
            realworld_backend.article.model.UserProfile currentProfile = userProfileRepository.findByUserAuthId(currentUser.userId()).orElse(null);
            if (currentProfile != null) {
            followingIds = new HashSet<>(
                    followRepository.findFollowingIdsByFollowerId(currentProfile.getId())
            );
            }
        }

        Set<Long> finalFollowingIds = followingIds;

        return comments.stream()
                .map(comment -> {

                    AuthorResponse authorDto =
                            AuthorResponse.from(
                                    comment.getUserProfile(),
                                    finalFollowingIds
                            );

                    CommentResponse dto = CommentResponse.from(comment, false);
                    dto.setAuthor(authorDto);

                    return dto;
                })
                .toList();
    }

    @Transactional
    public void deleteComment(String slug, Long commentId, CurrentAuthUser currentUser) {
        if (currentUser == null || currentUser.userId() == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        Article article = articleReposity.findBySlug(slug)
                .orElseThrow(() -> new BizException(ErrorCode.WITHOUT_ARTICLE));
        realworld_backend.article.model.UserProfile currentProfile = userProfileRepository.findByUserAuthId(currentUser.userId())
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BizException(ErrorCode.ARTICLE_NOT_FOUND));

        if (!comment.getArticle().getId().equals(article.getId())) {
            throw new BizException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        if (!comment.getUserProfile().getId().equals(currentProfile.getId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        commentRepository.delete(comment);
    }
}






