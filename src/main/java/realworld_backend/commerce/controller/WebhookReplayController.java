package realworld_backend.commerce.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import realworld_backend.common.dto.responseBody.ApiResponse;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.core.ProviderRawEvent;
import realworld_backend.commerce.service.webhook.WebhookOrchestrator;
import realworld_backend.commerce.service.webhook.core.EventAnticorruptionLayer;
import realworld_backend.commerce.service.webhook.core.WebhookDecision;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Profile("local")
@RestController
@RequestMapping("/api/debug/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookReplayController {
    private final WebhookOrchestrator webhookOrchestrator;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/replay", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> replayStripeWebhook(
            @RequestBody String payload,
            @RequestParam(value = "provider", defaultValue = "STRIPE") String provider
    ) {
        ProviderRawEvent rawEvent = parseReplayRawEvent(payload, provider);
        WebhookDecision decision = webhookOrchestrator.processReplay(payload, rawEvent, provider);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", provider);
        data.put("eventId", rawEvent.getEventId());
        data.put("eventType", rawEvent.getType() == null ? null : rawEvent.getType().name());
        data.put("rawType", rawEvent.getRawType());
        data.put("terminal", decision.terminal());
        data.put("httpStatus", decision.httpStatus() == null ? null : decision.httpStatus().value());
        data.put("needUpsertAbnormal", decision.needUpsertAbnormal());
        data.put("abnormalOrderType", decision.abnormalOrderType() == null ? null : decision.abnormalOrderType().name());
        data.put("eventStatus", decision.eventStatus() == null ? null : decision.eventStatus().name());
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    private ProviderRawEvent parseReplayRawEvent(String payload, String provider) {
        JsonNode root = readTree(payload);
        JsonNode dataNode = root.path("data");
        JsonNode objectNode = dataNode.path("object");

        if (root.isMissingNode() || root.isNull() || !root.isObject()) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }
        if (objectNode.isMissingNode() || objectNode.isNull() || objectNode.isEmpty()) {
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }

        String eventId = textOrNull(root, "id");
        String rawType = textOrNull(root, "type");
        Long created = root.hasNonNull("created") ? root.path("created").asLong() : null;
        Boolean livemode = root.hasNonNull("livemode") ? root.path("livemode").asBoolean() : null;

        if (eventId == null || rawType == null) {
            throw new BizException(ErrorCode.WEBHOOK_DATA_MISSING);
        }

        BusinessEventType eventType = EventAnticorruptionLayer.convertProviderEvent(rawType);
        log.info("webhook replay payload parsed, provider={}, eventId={}, rawType={}, eventType={}, created={}, livemode={}",
                provider, eventId, rawType, eventType, created, livemode);
        return ProviderRawEvent.builder()
                .provider(provider)
                .eventId(eventId)
                .type(eventType)
                .created(created)
                .livemode(livemode)
                .rawType(rawType)
                .rawObjectJson(objectNode.toString())
                .build();
    }

    private JsonNode readTree(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (IOException e) {
            throw new BizException(ErrorCode.JSON_ERROR);
        }
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        return field.isMissingNode() || field.isNull() || field.asText().isBlank() ? null : field.asText();
    }
}
