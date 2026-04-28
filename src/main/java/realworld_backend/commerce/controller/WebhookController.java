package realworld_backend.commerce.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import realworld_backend.commerce.service.WebhookOrchestrator;
import realworld_backend.commerce.service.core.WebhookDecision;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j

/**
 * Stripe webhook adapter.
 * Maps internal exception categories to Stripe-facing HTTP semantics.
 */
public class WebhookController {
    private final WebhookOrchestrator webhookOrchestrator;
    @Value("${stripe.webhook-secret}")
    private String endpointSecret;

    /**
     * Terminal errors return 200; recoverable errors return non-2xx for Stripe retry.
     */
    @PostMapping("/stripeOrderAcceptor")
    public ResponseEntity<String> acceptStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature") String sigHeader
    ) {

        WebhookDecision decision =
                webhookOrchestrator.process(payload, sigHeader, endpointSecret, "STRIPE");
        String body = decision.terminal() ? "accepted_terminal" : "retry_later";
        return ResponseEntity.status(decision.httpStatus()).body(body);

    }
}
