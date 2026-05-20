package realworld_backend.common.web.resolver;

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
import realworld_backend.auth.api.request.CurrentAuthUser;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.common.exception.TokenInvalidException;

/**
 * Resolves {@link CurrentAuthUser} arguments from the active security context.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {


    // Resolves only parameters explicitly marked with @CurrentUser.
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().equals(CurrentAuthUser.class);
    }

    // Builds the lightweight auth user from JWT claims when authentication is present.
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        boolean required = annotation != null && annotation.required();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            // Pull the minimum user context needed by controllers from the JWT.
            if (authentication.getPrincipal() instanceof Jwt jwt) {
                Long userId = jwt.getClaim("userId");
                String sessionId = jwt.getClaim("sessionId");

                if (userId == null || sessionId == null) {
                    if (required) {
                        throw new TokenInvalidException(ErrorCode.UNAUTHORIZED);
                    }
                    return null;
                }
                return new CurrentAuthUser(userId, sessionId);
            }
        }
        if (required) {
            throw new TokenInvalidException(ErrorCode.UNAUTHORIZED);
        }
        return null;
    }
}
