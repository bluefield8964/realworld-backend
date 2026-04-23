package realworld_backend.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import realworld_backend.auth.model.Role;

public interface RoleRepository extends JpaRepository<Role,Long> {


}

