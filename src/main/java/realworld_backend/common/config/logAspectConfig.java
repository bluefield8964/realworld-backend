package realworld_backend.common.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


@Aspect
@Component
@Slf4j
public class logAspectConfig {

    @Around("execution(* realworld_backend.controller..*(..))")
    public Object log(ProceedingJoinPoint pjp) throws Throwable {

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attrs != null) {
            String uri = attrs.getRequest().getRequestURI();

            if (uri.startsWith("/api/webhook")) {
                return pjp.proceed(); // dont make any process
            }
        }


        long start = System.currentTimeMillis();
        String method = pjp.getSignature().toShortString();
        Object[] args = pjp.getArgs();  // dont use this list , it could cause serialization
        Object result = pjp.proceed(); //proceed original method

        long cost = System.currentTimeMillis() - start;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = 0L;
        String username = "noBody";
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {

            userId = jwt.getClaim("userId");
            username = jwt.getClaim("username");
        }
        log.info("username={},userId={}, method={},  cost={}ms", username, userId, method, cost);
        return result;
    }
}

