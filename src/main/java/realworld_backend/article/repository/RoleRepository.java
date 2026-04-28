package realworld_backend.article.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import realworld_backend.article.model.Role;

public interface RoleRepository extends JpaRepository<Role,Long> {


}

