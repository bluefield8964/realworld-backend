package realworld_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.model.articleModule.Article;
import realworld_backend.model.articleModule.Comment;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment,Long> {


    List<Comment> findByArticleOrderByCreatedAtDesc(Article article);
}
