package realworld_backend.config;



import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;


@Configuration
public class JwtConfig {
    private static final String SECRT_KEY = "lentionDelia";

    @Bean
    public JwtEncoder jwtEncoder(){
        SecretKey hmacSHA256 = new SecretKeySpec(SECRT_KEY.getBytes(), "HmacSHA256");

        return new NimbusJwtEncoder(new ImmutableSecret<>(hmacSHA256));

    }
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey key = new SecretKeySpec(SECRT_KEY.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
