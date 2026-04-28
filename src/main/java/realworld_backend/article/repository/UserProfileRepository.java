package realworld_backend.article.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.article.model.UserProfile;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByEmail(String email);


    Optional<UserProfile> findByUsername(String username);

    Optional<UserProfile> findByUsernameOrEmail(String username, String email);
}

