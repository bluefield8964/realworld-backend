package realworld_backend.article.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.auth.model.User;


@Entity
@Table(name = "user_follow",
        uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "following_id"}))
@AllArgsConstructor
@NoArgsConstructor
@Data@Builder

public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 鐠嬩礁鍙у▔銊ュ焼娴?
    @ManyToOne
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    // 鐞氼偄鍙у▔銊ф畱娴?
    @ManyToOne
    @JoinColumn(name = "following_id", nullable = false)
    private User following;

}

