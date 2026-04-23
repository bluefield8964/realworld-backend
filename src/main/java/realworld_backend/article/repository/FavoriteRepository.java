package realworld_backend.article.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import realworld_backend.article.model.Favorite;

import java.util.List;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite,Long> {

    // 閸掋倖鏌囬弰顖氭儊閺€鎯版閿涘牐铔嬬槐銏犵穿閿涘矂娼敮绋挎彥閿?
    boolean existsByUserIdAndArticleId(Long userId, Long articleId);

    // 閸掔娀娅庨弨鎯版
    void deleteByUserIdAndArticleId(Long userId, Long articleId);

    @Query("SELECT f.article.id FROM Favorite f WHERE f.user.id = :userId AND f.article.id IN :articleIds")
    List<Long> findArticleIdsByUserIdAndArticleIds(Long userId, List<Long> articleIds);

}

