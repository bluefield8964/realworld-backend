package realworld_backend.auth.domain.service;

import realworld_backend.auth.domain.exception.UserAuthException;
import realworld_backend.auth.domain.model.UserAuthProfile;

public interface AccountStatusChecker {
    void checkLoginAllowed(UserAuthProfile user) throws UserAuthException;}
