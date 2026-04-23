package realworld_backend.article.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.dto.responseBody.ProfileResponse;
import realworld_backend.common.web.resolver.CurrentUser;
import realworld_backend.auth.model.User;
import realworld_backend.article.service.ProfileService;

@Controller
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService profileService;

    @GetMapping("/profiles/{username}")
    public ApiResponse<ProfileResponse> getProfile(
            @PathVariable String username,
            @CurrentUser User currentUser // could be null, as didnt login
    ) {
        ProfileResponse response = profileService.getProfile(username, currentUser);
        return ApiResponse.success(response);
    }

    @PostMapping("/profiles/{username}/follow")
    public ApiResponse<ProfileResponse> followUser(
            @PathVariable String username,
            @CurrentUser User currentUser // must be existed
    ) {

        if (currentUser == null)
            throw new RuntimeException("Unauthorized");

        ProfileResponse response = profileService.follow(username, currentUser);
        return ApiResponse.success(response);
    }

    @DeleteMapping("/profiles/{username}/follow")
    public ApiResponse<ProfileResponse> unfollowUser(
            @PathVariable String username,
            @CurrentUser User currentUser
    ) {

        if (currentUser == null) {
            throw new RuntimeException("Unauthorized");
        }

        ProfileResponse response = profileService.unfollow(username, currentUser);
        return ApiResponse.success(response);
    }

}

