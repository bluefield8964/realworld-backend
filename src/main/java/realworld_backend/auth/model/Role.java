package realworld_backend.auth.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor@Builder

public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 鐟欐帟澹婇崥宥忕窗ADMIN / USER / MANAGER
    @Column(unique = true, nullable = false)
    private String name;

    private String description;

}
