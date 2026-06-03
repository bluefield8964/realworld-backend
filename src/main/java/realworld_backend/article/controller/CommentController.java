package realworld_backend.article.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import realworld_backend.article.service.CommentService;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.common.dto.requestBody.CreateCommentRequest;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.dto.responseBody.CommentResponse;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.common.web.resolver.CurrentUser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/articles/{slug}/comments")
    public ResponseEntity<ApiResponse<?>> createComment(
            @PathVariable String slug,
            @RequestBody CreateCommentRequest request,
            @CurrentUser(required = true) CurrentAuthUser currentUser
    ) {
        CommentResponse response =
                commentService.createComment(slug, request, currentUser);

        Map<String, Object> data = new HashMap<>();
        data.put("comment", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));

    }

    @GetMapping("/articles/{slug}/comments")
    public ApiResponse<Map<String, Object>> getComments(
            @PathVariable String slug,
            @CurrentUser CurrentAuthUser currentUser
    ) {

        List<CommentResponse> comments =
                commentService.getComments(slug, currentUser);
        Map<String, Object> data = new HashMap<>();
        data.put("comments", comments);

        return ApiResponse.success(data);
    }

    @DeleteMapping("/articles/{slug}/comments/{id}")
    public ResponseEntity<?> deleteComment(
            @PathVariable String slug,
            @PathVariable Long id,
            @CurrentUser(required = true) CurrentAuthUser currentUser
    ) {
        if (currentUser == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        commentService.deleteComment(slug, id, currentUser);
        return ResponseEntity.noContent().build();
    }
}

