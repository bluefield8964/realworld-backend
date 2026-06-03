package realworld_backend.auth.security;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import realworld_backend.auth.domain.model.UserAuthProfile;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class TokenTool {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    private final RedisTemplate<String, Object> redisTemplate;

    public String generateAuthToken(UserAuthProfile user, String sessionId) {
        Instant now = Instant.now();
        String oldToken = (String) redisTemplate.opsForValue().get("Bearer_id:" + user.getId());
        if (oldToken != null) {
            redisTemplate.delete("Bearer:" + oldToken);
        }
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("sessionId", sessionId)
                .claim("id", user.getId())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        String tokenValue = jwtEncoder.encode(
                JwtEncoderParameters.from(claims)).getTokenValue();
        //token: token ->email
        //token : email -> token
        //in this case , the front token can be deleted after the latter token will be generated
        redisTemplate.opsForValue().set("Bearer:" + tokenValue, user.getId(), Duration.ofHours(1));
        redisTemplate.opsForValue().set("Bearer_id:" + user.getId(), tokenValue, Duration.ofHours(1));
        return tokenValue;
    }
}

