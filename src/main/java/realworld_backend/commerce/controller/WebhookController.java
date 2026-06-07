package realworld_backend.commerce.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import realworld_backend.commerce.service.webhook.WebhookOrchestrator;
import realworld_backend.commerce.service.webhook.core.WebhookDecision;

/**
 * Stripe webhook adapter.
 * Maps internal exception categories to Stripe-facing HTTP semantics.
 */
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
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
        log.info("stripe webhook ingress received, path=/api/webhook/stripeOrderAcceptor, payloadLength={}, signaturePresent={}",
                payload == null ? 0 : payload.length(),
                sigHeader != null && !sigHeader.isBlank());
        WebhookDecision decision =
                webhookOrchestrator.process(payload, sigHeader, endpointSecret, "STRIPE");
        String body = decision.terminal() ? "accepted_terminal" : "retry_later";
        log.info("stripe webhook ingress finished, httpStatus={}, terminal={}, body={}",
                decision.httpStatus(),
                decision.terminal(),
                body);
        return ResponseEntity.status(decision.httpStatus()).body(body);
    }
}
