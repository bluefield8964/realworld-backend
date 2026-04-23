package realworld_backend.article.service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.common.dto.responseBody.ProfileResponse;
import realworld_backend.article.model.Follow;
import realworld_backend.auth.model.User;
import realworld_backend.article.repository.FollowRepository;
import realworld_backend.auth.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    public ProfileResponse getProfile(String username, User currentUser) {
        User following = userRepository.findByUsername(username).orElseThrow(() -> new BizException(ErrorCode.FOLLOWING_DOES_NOT_EXIT));
        ProfileResponse profileResponse = new ProfileResponse();
        if (currentUser != null) {
            boolean existsByFollowerIdAndFollowingId = followRepository.existsByFollowerIdAndFollowingId(following.getId(), currentUser.getId());
            profileResponse.setFollowing(existsByFollowerIdAndFollowingId);
        } else {
            profileResponse.setFollowing(false);
        }
        return profileResponse;
    }

    public ProfileResponse follow(String username, User currentUser) {
        User following = userRepository.findByUsername(username).orElseThrow(() -> new BizException(ErrorCode.FOLLOWING_DOES_NOT_EXIT));
        if (currentUser.getId().equals(following.getId())) {
            throw new BizException(ErrorCode.CAN_NOT_FOLLOW_OR_UNFOLLOW_YOURSELF);
        }
        boolean exists = followRepository.existsByFollowerIdAndFollowingId(currentUser.getId(), following.getId());
        if (!exists) {
            Follow follow = new Follow();
            follow.setFollower(currentUser);
            follow.setFollowing(following);
            followRepository.save(follow);
        }
        ProfileResponse profile = new ProfileResponse(following);
        profile.setFollowing(true);
        return profile;
    }

    public ProfileResponse unfollow(String username, User currentUser) {
        User following = userRepository.findByUsername(username).orElseThrow(() -> new BizException(ErrorCode.FOLLOWING_DOES_NOT_EXIT));
        if (currentUser.getId().equals(following.getId())) {
            throw new BizException(ErrorCode.CAN_NOT_FOLLOW_OR_UNFOLLOW_YOURSELF);
        }
        // find out  follow relationship
        followRepository.deleteByFollowerIdAndFollowingId(currentUser.getId(), following.getId());
        ProfileResponse profileResponse = new ProfileResponse(following);
        profileResponse.setFollowing(false);
        return profileResponse;

    }
}

