package realworld_backend.service.mediumService.impl;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.model.articleModule.Article;
import realworld_backend.model.articleModule.Favorite;
import realworld_backend.model.accountModile.User;
import realworld_backend.repository.ArticleRepository;
import realworld_backend.repository.FavoriteRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ArticleRepository articleRepository;

    @Transactional
    public void favoriteArticle(User user, Article article) {

        // prevent favorite overtimes
        boolean exists = favoriteRepository.existsByUserIdAndArticleId(user.getId(), article.getId());
        if (exists) return;

        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favorite.setArticle(article);
        favorite.setCreatedAt(LocalDateTime.now());

        favoriteRepository.save(favorite);

        // ⭐ 同步更新计数（避免 count 查询）
        article.setFavoritesCount(article.getFavoritesCount() + 1);
        articleRepository.save(article);
    }

    @Transactional
    public void unfavoriteArticle(User user, Article article) {

        favoriteRepository.deleteByUserIdAndArticleId(user.getId(), article.getId());

        article.setFavoritesCount(article.getFavoritesCount() - 1);
        articleRepository.save(article);
    }

    //get user's favorite articles Id
    public Set<Long> getFavoritedArticleIds(User user, List<Long> articleIds) {

        if (user == null) return Collections.emptySet();

        List<Long> ids = favoriteRepository
                .findArticleIdsByUserIdAndArticleIds(user.getId(), articleIds);

        return new HashSet<>(ids);
    }

    public boolean checkFavorite(User user, Article article) {
        // 防止重复收藏
        return favoriteRepository.existsByUserIdAndArticleId(user.getId(), article.getId());
    }

}
