package realworld_backend.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import realworld_backend.auth.domain.enumerous.LoginIdentifierType;
import realworld_backend.auth.domain.model.UserAuthProfile;

public interface UserAuthProfileRepository extends JpaRepository<UserAuthProfile, Long> {
    UserAuthProfile findByEmail(String identifier);

    UserAuthProfile findByUsername(String identifier);

    @Modifying
    @Query("""
        update UserAuthProfile u
        set u.passwordHash = :passwordHash
        where u.id = :userId
    """)
    int updatePasswordHashByUserId(@Param("userId") Long userId,
                                   @Param("passwordHash") String passwordHash);
}
