package realworld_backend.tool;


import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import realworld_backend.config.RedisConfig;
import realworld_backend.dto.ErrorCode;
import realworld_backend.exception.TokenExpiredException;
import realworld_backend.exception.TokenInvalidException;
import realworld_backend.model.User;
import realworld_backend.service.RegisterService;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static realworld_backend.dto.ErrorCode.TOKEN_EXPIRED;

@Component
public class TokenTool {
    private final String SECRET_KEY = "lentionDelia_the_will_change_since_truth_break_out";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public TokenTool(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
    }

    @Autowired
    private RedisTemplate<String,Object> redisTemplate;
    @Autowired
    private RegisterService registerService;

    public String generateToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getEmail())              // 主体（谁）
                .claim("username", user.getUsername())
                .claim("Email", user.getEmail())// 自定义字段
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))      // 1小时过期
                .build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
        redisTemplate.opsForValue().set("token:"+tokenValue, user.getEmail(),1, TimeUnit.HOURS);
        return tokenValue;
    }

    public String decodeToken(String token) {

        try {
            SignedJWT jwt = SignedJWT.parse(token);
            Date expirationTime = jwt.getJWTClaimsSet().getExpirationTime();
            if (expirationTime != null && expirationTime.before(new Date())) {
                throw new TokenExpiredException(TOKEN_EXPIRED);
            }
            return jwtDecoder.decode(token).getSubject();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (
                RuntimeException e) { // jwtDecoder probably throw this ex while token was change or token got some abnormal situation
            throw new TokenInvalidException(ErrorCode.TOKEN_INVALID);
        }
    }



}
