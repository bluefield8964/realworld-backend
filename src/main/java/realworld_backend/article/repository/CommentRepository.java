package realworld_backend.article.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.article.model.Article;
import realworld_backend.article.model.Comment;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment,Long> {


    List<Comment> findByArticleOrderByCreatedAtDesc(Article article);
}

