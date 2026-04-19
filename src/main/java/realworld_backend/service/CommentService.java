package realworld_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import realworld_backend.dto.Exception.BizException;
import realworld_backend.dto.Exception.ErrorCode;
import realworld_backend.dto.requestBody.CreateCommentRequest;
import realworld_backend.dto.responseBody.AuthorResponse;
import realworld_backend.dto.responseBody.CommentResponse;
import realworld_backend.model.articleModule.Article;
import realworld_backend.model.articleModule.Comment;
import realworld_backend.model.accountModile.User;
import realworld_backend.repository.ArticleRepository;
import realworld_backend.repository.CommentRepository;
import realworld_backend.repository.FollowRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final ArticleRepository articleReposity;
    private final FavoriteService favoriteService;
    private final CommentRepository commentRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final FollowRepository followRepository;

    public CommentResponse createComment(
            String slug,
            CreateCommentRequest request,
            User currentUser
    ) {

        // 1️⃣ 找文章
        Article article = articleReposity.findBySlug(slug)
                .orElseThrow(() -> new BizException(ErrorCode.WITHOUT_ARTICLE));

        // 2️⃣ 创建 Comment
        Comment comment = new Comment();
        comment.setBody(request.getComment().getBody());
        comment.setAuthor(currentUser);
        comment.setArticle(article);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());

        // 3️⃣ 保存
        commentRepository.save(comment);

        // 4️⃣ 返回 DTO
        return CommentResponse.from(comment, false);
    }


    public List<CommentResponse> getComments(String slug, User currentUser) {


        // 1️⃣ 找文章
        Article article = articleReposity.findBySlug(slug)
                .orElseThrow(() -> new BizException(ErrorCode.WITHOUT_ARTICLE));

        // 2️⃣ 查评论（按时间排序）
        List<Comment> comments =
                commentRepository.findByArticleOrderByCreatedAtDesc(article);

        // 3️⃣ 查 following（一次性查，避免 N+1）
        Set<Long> followingIds = Collections.emptySet();

        if (currentUser != null) {
            followingIds = new HashSet<>(
                    followRepository.findFollowingIdsByFollowerId(currentUser.getId())
            );
        }

        Set<Long> finalFollowingIds = followingIds;

        // 4️⃣ 转 DTO
        return comments.stream()
                .map(comment -> {

                    AuthorResponse authorDto =
                            AuthorResponse.from(
                                    comment.getAuthor(),
                                    finalFollowingIds
                            );

                    CommentResponse dto = CommentResponse.from(comment, false);
                    dto.setAuthor(authorDto);

                    return dto;
                })
                .toList();
    }
}




