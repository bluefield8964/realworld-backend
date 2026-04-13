package realworld_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "user_follow",
        uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "following_id"}))
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 谁关注别人
    @ManyToOne
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    // 被关注的人
    @ManyToOne
    @JoinColumn(name = "following_id", nullable = false)
    private User following;

}
