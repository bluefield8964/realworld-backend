package realworld_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import realworld_backend.model.accountModile.Role;

public interface RoleRepository extends JpaRepository<Role,Long> {


}
