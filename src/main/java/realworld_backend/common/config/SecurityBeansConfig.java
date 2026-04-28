package realworld_backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class SecurityBeansConfig {
    @Bean
    public PasswordEncoder passwordEncoder(@Value("${security.password.id-for-encode:bcrypt}") String idForEncode,
                                           @Value("${security.password.bcrypt-cost:12}") int bcryptCost) {


        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder(bcryptCost));
        encoders.put("noop", NoOpPasswordEncoder.getInstance()); // Optional
        encoders.put("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()); // Optional
        encoders.put("scrypt", SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8());  // Optional
        encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());  // Optional

        return new DelegatingPasswordEncoder(idForEncode, encoders);
    }
}
