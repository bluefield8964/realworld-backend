package realworld_backend.config;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import realworld_backend.tool.JwtAccessDeniedEntryPoint;
import realworld_backend.tool.JwtAuthenticationEntryPoint;
import realworld_backend.tool.JwtAuthenticationFilter;
import realworld_backend.tool.TokenTool;

@Configuration
@EnableWebSecurity
public class SecurityFilterConfig {
    @Autowired
    private JwtAuthenticationFilter jwtFilter;

    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    private JwtAccessDeniedEntryPoint accessDeniedEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http){
        return http.csrf(crsf-> crsf.disable())
                .sessionManagement(session->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth->auth
                        .requestMatchers("/login","/homePage").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedEntryPoint))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class).build();

    }

}
