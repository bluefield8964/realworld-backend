package realworld_backend.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import realworld_backend.common.web.resolver.UserInsolverImpl;


import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebInsolverConfig implements WebMvcConfigurer {

    private final UserInsolverImpl userInsolverImpl;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(userInsolverImpl);
    }
}

