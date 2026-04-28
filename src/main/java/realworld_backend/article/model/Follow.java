package realworld_backend.article.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


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

    // User who follows another user.
    @ManyToOne
    @JoinColumn(name = "follower_id", nullable = false)
    private UserProfile follower;

    // User being followed.
    @ManyToOne
    @JoinColumn(name = "following_id", nullable = false)
    private UserProfile following;

}
