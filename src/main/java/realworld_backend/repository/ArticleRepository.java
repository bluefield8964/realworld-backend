package realworld_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.model.Articles;

import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Articles,Long> {
    Optional<Articles> findById(Long id);
    Articles save(Articles articles);

}
