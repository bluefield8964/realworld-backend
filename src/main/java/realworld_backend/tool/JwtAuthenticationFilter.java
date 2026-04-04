package realworld_backend.tool;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import realworld_backend.dto.ErrorCode;
import realworld_backend.exception.TokenExpiredException;
import realworld_backend.exception.TokenInvalidException;
import realworld_backend.model.Role;
import realworld_backend.model.User;
import realworld_backend.service.RegisterService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private TokenTool tokenTool;
    @Autowired
    private RegisterService registerService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // 1. No token → anonymous
        if (header == null || !header.startsWith("Token ")) {
            filterChain.doFilter(request, response);
            return;
        }

        //SecurityContextHolder still contain auth
        if (auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }


        // jwt logic
        String token = header.substring("Token ".length());
        //in the parseToken will extract user from redis or from DB
        User user;
        try {
            user = registerService.findByToken(token);
        } catch (TokenExpiredException e) {
            request.setAttribute("jwt_error", ErrorCode.TOKEN_EXPIRED);
            filterChain.doFilter(request, response);
            return;
        } catch (TokenInvalidException e) {
            request.setAttribute("jwt_error", ErrorCode.TOKEN_INVALID);
            filterChain.doFilter(request, response);
            return;
        }

        //make sure whether this token was fabric or not
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        //make suer this is a real and exit user
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Role role : user.getRoles()) {
            authorities.add(
                    new SimpleGrantedAuthority("ROLE_" + role.getName())
            );
        }

        // construct authentic object
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                user,
                null,
                authorities // 权限（后面再加）
        );

        //setGlobleContext
        SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
        filterChain.doFilter(request, response);
    }
}
