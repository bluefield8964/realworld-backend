package realworld_backend.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import realworld_backend.dto.Exception.BizException;
import realworld_backend.model.commerceModule.WebhookDecisionException;
import realworld_backend.service.CommerceService.core.WebhookDecision;
import realworld_backend.service.CommerceService.core.WebhookErrorPolicy;
import realworld_backend.service.CommerceService.service.WebhookService;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
/**
 * Stripe webhook adapter.
 * Maps internal exception categories to Stripe-facing HTTP semantics.
 */
public class WebhookController {
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    public final WebhookService webhookService;
    private final WebhookErrorPolicy webhookErrorPolicy;
    @Value("${stripe.webhook-secret}")
    private String endpointSecret;

    /**
     * Terminal errors return 200; recoverable errors return non-2xx for Stripe retry.
     */
    @PostMapping("/stripeOrderAcceptor")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature") String sigHeader
    ) {
        try {
            webhookService.handleStripeEvent(payload, sigHeader, endpointSecret, "stripe");
            return ResponseEntity.ok("success");
        } catch (Exception e) {
            if (e instanceof WebhookDecisionException wd) {
                WebhookDecision decision = ((WebhookDecisionException) e).getDecision();
                String body = decision.terminal() ? "accepted_terminal" : "retry_later";
                return ResponseEntity.status(decision.httpStatus()).body(body);
            }
            //unknow error required try one more time webhook
            return  ResponseEntity.status(500).body("retry_later");
        }
    }
}
