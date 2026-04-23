package realworld_backend.commerce.model;

import realworld_backend.commerce.service.core.WebhookDecision;

public class WebhookDecisionException  extends RuntimeException{
    private final WebhookDecision decision;

    public WebhookDecisionException(WebhookDecision decision, Throwable cause) {
        super(cause.getMessage(), cause);
        this.decision = decision;
    }

    public WebhookDecision getDecision() {
        return decision;
    }
}

