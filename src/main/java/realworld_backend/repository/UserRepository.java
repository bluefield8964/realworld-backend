package realworld_backend.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.model.accountModile.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);


    Optional<User> findByUsername(String username);

    // 查 username 或 email 是否存在
    Optional<User> findByUsernameOrEmail(String username, String email);
}
