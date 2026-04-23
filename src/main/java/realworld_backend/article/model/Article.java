package realworld_backend.article.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;


@Entity
@Table(name="articles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // 娑撳鏁?
    private String title;
    private String description;
    @Column(columnDefinition = "TEXT")
    private String body;

    // 鐚?core many to many
    @ManyToMany
    @JoinTable(
            name = "article_tags",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tagList = new HashSet<>();
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id") // 婢舵牠鏁?
    private Author author;
    @Column(unique = true)
    private String slug;
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    @Column(nullable = false)
    private Long favoritesCount=0L;

}

