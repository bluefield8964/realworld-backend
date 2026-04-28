package realworld_backend.article.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import realworld_backend.article.model.Favorite;

import java.util.List;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite,Long> {

    boolean existsByUserIdAndArticleId(Long userId, Long articleId);


    void deleteByUserIdAndArticleId(Long userId, Long articleId);

    @Query("SELECT f.article.id FROM Favorite f WHERE f.user.id = :userId AND f.article.id IN :articleIds")
    List<Long> findArticleIdsByUserIdAndArticleIds(Long userId, List<Long> articleIds);

}

