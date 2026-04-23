package realworld_backend.article.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import realworld_backend.article.model.Article;
import realworld_backend.article.model.Author;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {


    List<Article> findByAuthorId(Long authorId);

    // 棣冩啝 select by one tag
    @Query("select distinct a from Article a join a.tagList t where t.name = :tag")
    List<Article> findByTag(@Param("tag") String tag);

    // 棣冩啝 select by any tags
    @Query("select distinct a from Article a join a.tagList t where t.name in :tags")
    List<Article> findByTags(@Param("tags") Set<String> tags);

    Optional<Article> findBySlug(String slug);

    // pageable drive the sql transfer into limited sql
    List<Article> findByAuthorIdInOrderByCreatedAtDesc(List<Long> followingIds, Pageable pageable);

    int countByAuthorIdIn(List<Long> followingIds);

    void deleteBySlug(String slug);

    @Query("""
                SELECT a FROM Article a
                WHERE (:authorId IS NULL OR a.author.id = :authorId)
                  AND (:tag IS NULL OR :tag MEMBER OF a.tagList)
                ORDER BY a.createdAt DESC
            """)
    List<Article> findArticles(Author author, String tag);
}

