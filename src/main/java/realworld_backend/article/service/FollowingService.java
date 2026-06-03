package realworld_backend.article.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.article.model.UserProfile;
import realworld_backend.article.repository.FollowRepository;
import realworld_backend.article.repository.UserProfileRepository;
import realworld_backend.auth.api.request.CurrentAuthUser;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FollowingService {
    private final FollowRepository followRepository;
    private final UserProfileRepository userProfileRepository;

    public Set<Long> findFollowingIdsByFollowerId(CurrentAuthUser user) {
        if (user == null || user.userId() == null) {
            return Set.of();
        }
        UserProfile follower = userProfileRepository.findByUserAuthId(user.userId()).orElse(null);
        if (follower == null) {
            return Set.of();
        }
        return new HashSet<>(followRepository.findFollowingIdsByFollowerId(follower.getId()));
    }



}
