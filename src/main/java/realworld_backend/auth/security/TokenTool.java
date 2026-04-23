package realworld_backend.auth.security;


import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.common.exception.TokenExpiredException;
import realworld_backend.common.exception.TokenInvalidException;
import realworld_backend.auth.model.User;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static realworld_backend.common.exception.ErrorCode.TOKEN_EXPIRED;

@Component
@RequiredArgsConstructor
public class TokenTool {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    private final RedisTemplate<String, Object> redisTemplate;


    public String generateToken(User user) {
        Instant now = Instant.now();
        String oldToken = (String) redisTemplate.opsForValue().get("Bearer_id:" + user.getId());
        if (oldToken != null) {
            redisTemplate.delete("Bearer:" + oldToken);
        }
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("username", user.getUsername())
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


    public JWTClaimsSet gainJwt(String token) {

        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWTClaimsSet jwtClaimsSet = jwt.getJWTClaimsSet();
            if (jwtClaimsSet.getExpirationTime() != null && jwtClaimsSet.getExpirationTime().before(new Date())) {
                throw new TokenExpiredException(TOKEN_EXPIRED);
            }
            return jwtClaimsSet;
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (
                RuntimeException e) { // jwtDecoder probably throw this ex while token was change or token got some abnormal situation
            throw new TokenInvalidException(ErrorCode.TOKEN_INVALID);
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    public long tokenRemainingTime(String token) throws ParseException {
        SignedJWT jwt = SignedJWT.parse(token);
        Date expirationTime = jwt.getJWTClaimsSet().getExpirationTime();
        long now = System.currentTimeMillis();
        return (expirationTime.getTime() - now) / 1000;
    }


}

