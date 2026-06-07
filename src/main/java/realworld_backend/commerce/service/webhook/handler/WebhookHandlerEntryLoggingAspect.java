package realworld_backend.commerce.service.webhook.handler;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import realworld_backend.commerce.service.webhook.core.WebhookContext;

@Slf4j
@Aspect
@Component
public class WebhookHandlerEntryLoggingAspect {

    @Before("execution(* realworld_backend.commerce.service.webhook.handler..*.handle(..)) && args(ctx)")
    public void logHandlerEntry(JoinPoint joinPoint, WebhookContext ctx) {
        log.info(
                "webhook handler entry: handler={}, eventType={}, eventId={}, trackingId={}, providerTrackingId={}, attempts={}, provider={}",
                joinPoint.getTarget().getClass().getSimpleName(),
                ctx == null ? null : ctx.getEventType(),
                ctx == null ? null : ctx.getEventId(),
                ctx == null ? null : ctx.getTrackingId(),
                ctx == null ? null : ctx.getProviderTrackingId(),
                ctx == null ? null : ctx.getAttempts(),
                ctx == null ? null : ctx.getProvider()
        );
    }
}
