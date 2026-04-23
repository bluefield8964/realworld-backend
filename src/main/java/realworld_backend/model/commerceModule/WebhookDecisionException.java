package realworld_backend.model.commerceModule;

import realworld_backend.service.CommerceService.core.WebhookDecision;

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
