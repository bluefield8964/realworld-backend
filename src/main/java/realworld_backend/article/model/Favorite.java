package realworld_backend.article.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.auth.model.User;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "favorites",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "article_id"})
)
@Data
@NoArgsConstructor
@AllArgsConstructor@Builder

public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 鐠嬩焦鏁归挊?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 閺€鎯版閸濐亞鐦掗弬鍥╃彿
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    // 閺€鎯版閺冨爼妫块敍鍫滀簰閸氬骸褰叉禒銉﹀笓鎼?/ 閹恒劏宕橀悽顭掔礆
    private LocalDateTime createdAt;


}
