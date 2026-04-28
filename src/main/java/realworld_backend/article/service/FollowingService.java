package realworld_backend.article.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.article.repository.FollowRepository;
import realworld_backend.auth.api.request.CurrentAuthUser;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FollowingService {
    private final FollowRepository followRepository;

    public Set<Long> findFollowingIdsByFollowerId(CurrentAuthUser user) {
        return new HashSet<>(followRepository.findFollowingIdsByFollowerId(user.userId()));
    }



}
