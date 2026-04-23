package realworld_backend.article.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.common.dto.requestBody.CreateCommentRequest;
import realworld_backend.common.dto.responseBody.AuthorResponse;
import realworld_backend.common.dto.responseBody.CommentResponse;
import realworld_backend.article.model.Article;
import realworld_backend.article.model.Comment;
import realworld_backend.auth.model.User;
import realworld_backend.article.repository.ArticleRepository;
import realworld_backend.article.repository.CommentRepository;
import realworld_backend.article.repository.FollowRepository;

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

        // 1閿斿繆鍎?閹电偓鏋冪粩?
        Article article = articleReposity.findBySlug(slug)
                .orElseThrow(() -> new BizException(ErrorCode.WITHOUT_ARTICLE));

        // 2閿斿繆鍎?閸掓稑缂?Comment
        Comment comment = new Comment();
        comment.setBody(request.getComment().getBody());
        comment.setAuthor(currentUser);
        comment.setArticle(article);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());

        // 3閿斿繆鍎?娣囨繂鐡?
        commentRepository.save(comment);

        // 4閿斿繆鍎?鏉╂柨娲?DTO
        return CommentResponse.from(comment, false);
    }


    public List<CommentResponse> getComments(String slug, User currentUser) {


        // 1閿斿繆鍎?閹电偓鏋冪粩?
        Article article = articleReposity.findBySlug(slug)
                .orElseThrow(() -> new BizException(ErrorCode.WITHOUT_ARTICLE));

        // 2閿斿繆鍎?閺屻儴鐦庣拋鐚寸礄閹稿妞傞梻瀛樺笓鎼村骏绱?
        List<Comment> comments =
                commentRepository.findByArticleOrderByCreatedAtDesc(article);

        // 3閿斿繆鍎?閺?following閿涘牅绔村▎鈩冣偓褎鐓￠敍宀勪缉閸?N+1閿?
        Set<Long> followingIds = Collections.emptySet();

        if (currentUser != null) {
            followingIds = new HashSet<>(
                    followRepository.findFollowingIdsByFollowerId(currentUser.getId())
            );
        }

        Set<Long> finalFollowingIds = followingIds;

        // 4閿斿繆鍎?鏉?DTO
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





