package realworld_backend.commerce.service.core;

public interface WebhookErrorPolicy {
    WebhookDecision classify(Throwable ex);
}

