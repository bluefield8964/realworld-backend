package realworld_backend.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import realworld_backend.auth.domain.model.AuthAuditLog;

public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog,Long> {

}
