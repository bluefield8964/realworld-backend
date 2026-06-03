package realworld_backend.auth.domain.service;

import realworld_backend.auth.domain.model.UserAuthProfile;

public interface PasswordVerifier {
    void verify(UserAuthProfile user, String rawPassword);
}
