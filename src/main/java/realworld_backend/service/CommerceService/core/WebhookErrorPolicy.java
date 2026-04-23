package realworld_backend.service.CommerceService.core;

public interface WebhookErrorPolicy {
    WebhookDecision classify(Throwable ex);
}
