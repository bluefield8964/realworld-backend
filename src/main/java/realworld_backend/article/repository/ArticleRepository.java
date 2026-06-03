package realworld_backend.article.repository;

import org.springframework.data.domain.Page;
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

    // Query articles by a single tag name
    @Query("select distinct a from Article a join a.tagList t where t.name = :tag")
    List<Article> findByTag(@Param("tag") String tag);

    // Query articles by any tag in the provided set
    @Query("select distinct a from Article a join a.tagList t where t.name in :tags")
    List<Article> findByTags(@Param("tags") Set<String> tags);

    Optional<Article> findBySlug(String slug);

    // pageable drive the sql transfer into limited sql
    List<Article> findByAuthorIdInOrderByCreatedAtDesc(Set<Long> followingIds, Pageable pageable);

    int countByAuthorIdIn(Set<Long> followingIds);

    void deleteBySlug(String slug);

    @Query("""
                SELECT DISTINCT a
                FROM Article a
                LEFT JOIN a.tagList t
                WHERE (:author IS NULL OR a.author = :author)
                  AND (:tag IS NULL OR t.name = :tag)
                  AND (:favoritedUserId IS NULL OR EXISTS (
                        SELECT 1 FROM Favorite f
                        WHERE f.article = a AND f.user.id = :favoritedUserId
                  ))
            """)
    Page<Article> findArticles(
            @Param("author") Author author,
            @Param("tag") String tag,
            @Param("favoritedUserId") Long favoritedUserId,
            Pageable pageable
    );

    @Query("""
                SELECT COUNT(DISTINCT a)
                FROM Article a
                LEFT JOIN a.tagList t
                WHERE (:author IS NULL OR a.author = :author)
                  AND (:tag IS NULL OR t.name = :tag)
                  AND (:favoritedUserId IS NULL OR EXISTS (
                        SELECT 1 FROM Favorite f
                        WHERE f.article = a AND f.user.id = :favoritedUserId
                  ))
            """)
    int countArticles(
            @Param("author") Author author,
            @Param("tag") String tag,
            @Param("favoritedUserId") Long favoritedUserId
    );
}

