package realworld_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import realworld_backend.dto.Exception.BizException;
import realworld_backend.dto.Exception.ErrorCode;
import realworld_backend.dto.requestBody.CreateCommentRequest;
import realworld_backend.dto.responseBody.ApiResponse;
import realworld_backend.dto.responseBody.CommentResponse;
import realworld_backend.insolver.CurrentUser;
import realworld_backend.model.accountModile.User;
import realworld_backend.service.mediumService.impl.CommentService;

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
