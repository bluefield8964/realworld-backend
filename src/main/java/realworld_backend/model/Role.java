package realworld_backend.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    // 👉 反向关系（可选，但推荐）
    @ManyToMany(mappedBy = "roles")
    private List<User> users;
}