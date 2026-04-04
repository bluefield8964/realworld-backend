package realworld_backend.insolver;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import realworld_backend.model.User;
import realworld_backend.repository.UserRepository;
import realworld_backend.tool.TokenTool;

@Component
public class UserInsolverImpl implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;
    private final TokenTool tokenTool;

    public UserInsolverImpl(UserRepository userRepository, TokenTool jwtUtil) {
        this.userRepository = userRepository;
        this.tokenTool = jwtUtil;
    }

    // 判断这个参数要不要处理
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(UserSolve.class)
                && parameter.getParameterType().equals(User.class);
    }

    // 真正注入值的地方
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();

        return null;
    }
}