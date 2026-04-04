package realworld_backend.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import realworld_backend.model.User;
import realworld_backend.tool.TokenTool;

@Aspect
@Component
public class logAspectConfig {

    private static final Logger log = LoggerFactory.getLogger(logAspectConfig.class);

    @Autowired
    private  TokenTool tokenTool;

    logAspectConfig(TokenTool tokenTool) {
        this.tokenTool=tokenTool;
    }

    @Around("execution(* com.yourapp.controller..*(..))")
    public Object log(ProceedingJoinPoint pjp) throws Throwable {


        long start = System.currentTimeMillis();

        String method = pjp.getSignature().toShortString();
        Object[] args = pjp.getArgs();
        Object result = pjp.proceed(); // 执行原方法

        long cost = System.currentTimeMillis() - start;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) auth.getPrincipal();
        log.info("user={}, method={}, args={}, cost={}ms",user.getEmail(),method,result,args.toString(),cost);
        return result;
    }
}
