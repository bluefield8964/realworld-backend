package realworld_backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import realworld_backend.model.Articles;
import realworld_backend.dto.ArticleResponse;
import realworld_backend.repository.ArticleRepository;

@Service
public class ArticleService {
    @Autowired
    private ArticleRepository articleReposity;

    public ArticleResponse createArticle(Articles articles) {
        ArticleResponse articleResponse = new ArticleResponse(null, articleReposity.save(articles));
        return articleResponse;
    }
}
