package realworld_backend.article.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.auth.model.User;

import java.time.LocalDateTime;

@Data
@Entity
@AllArgsConstructor
@Builder

@NoArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String body;

    @ManyToOne
    private User author;

    @ManyToOne
    private Article article;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
