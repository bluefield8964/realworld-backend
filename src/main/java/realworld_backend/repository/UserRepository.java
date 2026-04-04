package realworld_backend.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.model.Articles;
import realworld_backend.model.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository <User, Long> {

    Optional<User> findByEmail(String email);
    User save (User user);

}
