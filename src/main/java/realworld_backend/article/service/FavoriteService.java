package realworld_backend.article.service;

import realworld_backend.common.time.UtcTimeMapper;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.article.model.Article;
import realworld_backend.article.model.Favorite;
import realworld_backend.article.model.UserProfile;
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.article.repository.ArticleRepository;
import realworld_backend.article.repository.FavoriteRepository;
import realworld_backend.article.repository.UserProfileRepository;

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
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public void favoriteArticle(UserProfile user, Article article) {

        // prevent favorite overtimes
        boolean exists = favoriteRepository.existsByUserIdAndArticleId(user.getId(), article.getId());
        if (exists) return;

        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favorite.setArticle(article);
        favorite.setCreatedAt(UtcTimeMapper.nowUtc());

        favoriteRepository.save(favorite);


        article.setFavoritesCount(article.getFavoritesCount() + 1);
        articleRepository.save(article);
    }

    @Transactional
    public void unfavoriteArticle(UserProfile user, Article article) {

        favoriteRepository.deleteByUserIdAndArticleId(user.getId(), article.getId());

        article.setFavoritesCount(article.getFavoritesCount() - 1);
        articleRepository.save(article);
    }

    //get user's favorite articles Id
    public Set<Long> getFavoritedArticleIds(CurrentAuthUser user, List<Article> articles) {
        Set<Long> favoritedIds = Collections.emptySet();

        List<Long> articleIds = articles.stream()
                .map(Article::getId)
                .toList();


        if (user == null || user.userId() == null) return Collections.emptySet();
        UserProfile userProfile = userProfileRepository.findByUserAuthId(user.userId()).orElse(null);
        if (userProfile == null) return Collections.emptySet();

        List<Long> ids = favoriteRepository
                .findArticleIdsByUserIdAndArticleIds(userProfile.getId(), articleIds);


        favoritedIds=new HashSet<>(ids);
        return favoritedIds;
    }


    public boolean checkFavorite(CurrentAuthUser user, Article article) {
        if (user == null || user.userId() == null) {
            return false;
        }
        UserProfile userProfile = userProfileRepository.findByUserAuthId(user.userId()).orElse(null);
        if (userProfile == null) {
            return false;
        }
        return favoriteRepository.existsByUserIdAndArticleId(userProfile.getId(), article.getId());
    }

    public boolean isFavoritedByUser(Long userId, Long articleId) {
        if (userId == null || articleId == null) {
            return false;
        }
        return favoriteRepository.existsByUserIdAndArticleId(userId, articleId);
    }

}


