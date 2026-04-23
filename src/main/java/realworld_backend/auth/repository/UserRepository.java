package realworld_backend.auth.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.auth.model.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);


    Optional<User> findByUsername(String username);

    // 閺?username 閹?email 閺勵垰鎯佺€涙ê婀?
    Optional<User> findByUsernameOrEmail(String username, String email);
}

