package realworld_backend.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import realworld_backend.common.web.resolver.CurrentUserArgumentResolver;


import java.util.List;

/**
 * Registers custom MVC argument resolvers used by controller methods.
 */
@Configuration
@RequiredArgsConstructor
public class WebArgumentResolverConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}

