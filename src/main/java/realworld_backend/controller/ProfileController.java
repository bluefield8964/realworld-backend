package realworld_backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import realworld_backend.dto.responseBody.ApiResponse;
import realworld_backend.dto.responseBody.ProfileResponse;
import realworld_backend.insolver.CurrentUser;
import realworld_backend.model.User;
import realworld_backend.service.ProfileService;

@Controller
@RequestMapping("/api")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

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
