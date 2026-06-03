package realworld_backend.auth.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.auth.domain.enumerous.AccountStatus;

@Builder
@Entity
@Table(name = "user_auth_profiles")
@AllArgsConstructor
@Data
@NoArgsConstructor(force = true)
public class UserAuthProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private  Long id;
    private  String username;
    private  String email;
    private  String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(50)")
    private  AccountStatus status;
    private  boolean mfaEnabled;
}
