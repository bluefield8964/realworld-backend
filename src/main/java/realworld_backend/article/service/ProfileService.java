package realworld_backend.article.service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.article.model.Follow;
import realworld_backend.article.model.UserProfile;
import realworld_backend.article.repository.FollowRepository;
import realworld_backend.article.repository.UserProfileRepository;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.common.dto.responseBody.ProfileResponse;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final FollowRepository followRepository;
    private final UserProfileRepository userProfileRepository;

    public ProfileResponse getProfile(String username, CurrentAuthUser currentUser) {
        UserProfile following = userProfileRepository.findByUsername(username)
                .orElseThrow(() -> new BizException(ErrorCode.FOLLOWING_NOT_FOUND));
        ProfileResponse profileResponse = new ProfileResponse(following);
        if (currentUser != null) {
            UserProfile currentProfile = userProfileRepository.findByUserAuthId(currentUser.userId()).orElse(null);
            if (currentProfile == null) {
                profileResponse.setFollowing(false);
                return profileResponse;
            }
            boolean existsByFollowerIdAndFollowingId =
                    followRepository.existsByFollowerIdAndFollowingId(currentProfile.getId(), following.getId());
            profileResponse.setFollowing(existsByFollowerIdAndFollowingId);
        } else {
            profileResponse.setFollowing(false);
        }
        return profileResponse;
    }

    public ProfileResponse follow(String username, CurrentAuthUser currentUser) {
        UserProfile following = userProfileRepository.findByUsername(username)
                .orElseThrow(() -> new BizException(ErrorCode.FOLLOWING_NOT_FOUND));
        UserProfile follower = userProfileRepository.findByUserAuthId(currentUser.userId())
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        if (follower.getId().equals(following.getId())) {
            throw new BizException(ErrorCode.CANNOT_FOLLOW_SELF);
        }
        boolean exists = followRepository.existsByFollowerIdAndFollowingId(follower.getId(), following.getId());
        if (!exists) {
            Follow follow = new Follow();
            follow.setFollower(follower);
            follow.setFollowing(following);
            followRepository.save(follow);
        }
        ProfileResponse profile = new ProfileResponse(following);
        profile.setFollowing(true);
        return profile;
    }

    public ProfileResponse unfollow(String username, CurrentAuthUser currentUser) {
        UserProfile following = userProfileRepository.findByUsername(username)
                .orElseThrow(() -> new BizException(ErrorCode.FOLLOWING_NOT_FOUND));
        UserProfile follower = userProfileRepository.findByUserAuthId(currentUser.userId())
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        if (follower.getId().equals(following.getId())) {
            throw new BizException(ErrorCode.CANNOT_FOLLOW_SELF);
        }
        // find out  follow relationship
        followRepository.deleteByFollowerIdAndFollowingId(follower.getId(), following.getId());
        ProfileResponse profileResponse = new ProfileResponse(following);
        profileResponse.setFollowing(false);
        return profileResponse;

    }
}
