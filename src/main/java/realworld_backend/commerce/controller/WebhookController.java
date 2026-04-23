package realworld_backend.commerce.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import realworld_backend.common.exception.BizException;
import realworld_backend.commerce.model.WebhookDecisionException;
import realworld_backend.commerce.service.core.WebhookDecision;
import realworld_backend.commerce.service.core.WebhookErrorPolicy;
import realworld_backend.commerce.service.WebhookService;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j

/**
 * Stripe webhook adapter.
 * Maps internal exception categories to Stripe-facing HTTP semantics.
 */
public class WebhookController {
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

