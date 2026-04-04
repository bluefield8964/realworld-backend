package realworld_backend.dto;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.model.Articles;


@Table(name="articleResponse")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleResponse  {
    private Error error;
    private Articles articles;
}
