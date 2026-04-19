package realworld_backend.insolver;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import realworld_backend.model.accountModile.User;

@Component
public class UserInsolverImpl implements HandlerMethodArgumentResolver {


    // decide whether this parameter need to handle
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().equals(User.class);
    }

    // inject user
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = new User();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            // gain user info from jwt then inject into user
            if (authentication.getPrincipal() instanceof Jwt jwt) {
                user.setId(jwt.getClaim("userId"));
                user.setBio(jwt.getClaim("userBio"));
                user.setImage(jwt.getClaim("userImage"));
                user.setUsername(jwt.getClaim("username"));
            }
            return user;
        }
        return null;
    }
}