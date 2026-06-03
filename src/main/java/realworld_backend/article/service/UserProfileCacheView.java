package realworld_backend.article.service;

import realworld_backend.article.model.UserProfile;

import java.io.Serializable;

/**
 * Shared lightweight cache payload for user profile snapshots.
 * Kept public so accidental JSON serialization can still succeed.
 */
public record UserProfileCacheView(
        Long id,
        Long userAuthId,
        String username,
        String email,
        String bio,
        String image
) implements Serializable {
    static final String CACHE_KEY_PREFIX = "user:v2:";

    static UserProfileCacheView from(UserProfile user) {
        if (user == null) {
            return null;
        }
        return new UserProfileCacheView(
                user.getId(),
                user.getUserAuthId(),
                user.getUsername(),
                user.getEmail(),
                user.getBio(),
                user.getImage()
        );
    }

    UserProfile toEntity() {
        return UserProfile.builder()
                .id(id)
                .userAuthId(userAuthId)
                .username(username)
                .email(email)
                .bio(bio)
                .image(image)
                .build();
    }

    static String key(Long userId) {
        return CACHE_KEY_PREFIX + userId;
    }
}
