package realworld_backend.article.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import realworld_backend.article.model.Article;
import realworld_backend.article.model.Comment;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment,Long> {


    List<Comment> findByArticleOrderByCreatedAtDesc(Article article);

    @Modifying
    @Query("delete from Comment c where c.article = :article")
    void deleteByArticle(@Param("article") Article article);
}

