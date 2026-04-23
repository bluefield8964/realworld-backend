package realworld_backend.article.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.common.dto.requestBody.CreateCommentRequest;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.dto.responseBody.CommentResponse;
import realworld_backend.common.web.resolver.CurrentUser;
import realworld_backend.auth.model.User;
import realworld_backend.article.service.CommentService;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/articles/{slug}/comments")
    public ApiResponse<CommentResponse> createComment(
            @PathVariable String slug,
            @RequestBody CreateCommentRequest request,
            @CurrentUser User currentUser
    ) {

        if (currentUser == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        CommentResponse response =
                commentService.createComment(slug, request, currentUser);

        return ApiResponse.success(response);

    }

    @GetMapping("/articles/{slug}/comments")
    public ApiResponse<List<CommentResponse>> getComments(
            @PathVariable String slug,
            @CurrentUser User currentUser
    ) {

        List<CommentResponse> comments =
                commentService.getComments(slug, currentUser);

        return ApiResponse.success(comments);
    }
}

