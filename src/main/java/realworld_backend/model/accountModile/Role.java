package realworld_backend.model.accountModile;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 角色名：ADMIN / USER / MANAGER
    @Column(unique = true, nullable = false)
    private String name;

    private String description;

}