package realworld_backend.auth.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor(force = true)
@Data
public class TokenPair {
    private final String accessToken;
    private final String refreshToken;
    private final LocalDateTime accessTokenExpiresAt;
}
