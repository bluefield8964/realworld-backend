package realworld_backend.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;


@Entity
@Table(name="articles")
@Data
@NoArgsConstructor
@AllArgsConstructor

public class Articles {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // 主键
    private String title;
    private String description;
    @Column(columnDefinition = "TEXT")
    private String body;
    // ✅ tagList
    @ElementCollection
    @CollectionTable(name = "article_tags", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "tag")
    private List<String> tagList;
    @ManyToOne
    @JoinColumn(name = "author_id") // 外键
    private Author author;
    private String slug;
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    private Boolean favorited;
    private Long favoritesCount;
}
