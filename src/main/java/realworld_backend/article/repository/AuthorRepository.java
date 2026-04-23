package realworld_backend.article.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.article.model.Article;

@Repository
public interface AuthorRepository extends JpaRepository<Article, Long> {

}

