package realworld_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import realworld_backend.model.articleModule.Favorite;

import java.util.List;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite,Long> {

    // 判断是否收藏（走索引，非常快）
    boolean existsByUserIdAndArticleId(Long userId, Long articleId);

    // 删除收藏
    void deleteByUserIdAndArticleId(Long userId, Long articleId);

    @Query("SELECT f.article.id FROM Favorite f WHERE f.user.id = :userId AND f.article.id IN :articleIds")
    List<Long> findArticleIdsByUserIdAndArticleIds(Long userId, List<Long> articleIds);

}
