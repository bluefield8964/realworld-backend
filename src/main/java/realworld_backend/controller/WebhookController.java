package realworld_backend.controller;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import realworld_backend.service.WebhookService;

@RestController
@RequestMapping("/api/webhook")
public class WebhookController {
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    public final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Value("${stripe.webhook-secret}")
    private String endpointSecret;

    @PostMapping("/orderAcceptor")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {

        try {
            webhookService.handleStripeEvent(payload, sigHeader, endpointSecret);
        } catch (Exception e) {
            log.error("webhook error ", e);
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok("success");
    }

}
