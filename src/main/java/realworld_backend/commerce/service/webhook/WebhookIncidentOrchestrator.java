package realworld_backend.commerce.service.webhook;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.commerce.model.log.AbnormalDomainType;
import realworld_backend.commerce.model.log.WebhookIncident;
import realworld_backend.commerce.model.log.WebhookIncidentStatus;
import realworld_backend.commerce.model.log.WebhookIncidentType;
import realworld_backend.commerce.repository.WebhookIncidentRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookIncidentOrchestrator {
    private final WebhookIncidentRepository webhookIncidentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WebhookIncident recordIncident(
            String provider,
            AbnormalDomainType domainType,
            String eventId,
            String eventType,
            String requestId,
            String trackingId,
            String providerTrackingId,
            WebhookIncidentType incidentType,
            String reason,
            String errorMessage,
            String rawPayload
    ) {
        LocalDateTime now = UtcTimeMapper.nowUtc();
        String dedupeKey = resolveDedupeKey(provider, incidentType, eventId, requestId, rawPayload);
        String payloadDigest = digestPayload(rawPayload);

        Optional<WebhookIncident> existing = webhookIncidentRepository.findByDedupeKeyForUpdate(dedupeKey);
        if (existing.isPresent()) {
            WebhookIncident incident = existing.get();
            mergeIncident(
                    incident,
                    domainType,
                    eventId,
                    eventType,
                    requestId,
                    trackingId,
                    providerTrackingId,
                    reason,
                    errorMessage,
                    payloadDigest,
                    rawPayload,
                    now
            );
            webhookIncidentRepository.save(incident);
            return incident;
        }

        WebhookIncident incident = WebhookIncident.builder()
                .provider(provider)
                .domainType(domainType == null ? AbnormalDomainType.SYSTEM : domainType)
                .eventId(eventId)
                .eventType(eventType)
                .requestId(requestId)
                .trackingId(trackingId)
                .providerTrackingId(providerTrackingId)
                .dedupeKey(dedupeKey)
                .payloadDigest(payloadDigest)
                .rawPayload(rawPayload)
                .reason(reason)
                .errorMessage(errorMessage)
                .occurrenceCount(1)
                .incidentType(incidentType)
                .status(WebhookIncidentStatus.OPEN)
                .firstOccurredAt(now)
                .lastOccurredAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        webhookIncidentRepository.save(incident);
        return incident;
    }

    @Transactional(readOnly = true)
    public Optional<WebhookIncident> findIncidentSnapshot(
            String provider,
            WebhookIncidentType incidentType,
            String eventId,
            String requestId,
            String rawPayload
    ) {
        String dedupeKey = resolveDedupeKey(provider, incidentType, eventId, requestId, rawPayload);
        return webhookIncidentRepository.findByDedupeKey(dedupeKey);
    }

    private void mergeIncident(
            WebhookIncident incident,
            AbnormalDomainType domainType,
            String eventId,
            String eventType,
            String requestId,
            String trackingId,
            String providerTrackingId,
            String reason,
            String errorMessage,
            String payloadDigest,
            String rawPayload,
            LocalDateTime now
    ) {
        if (domainType != null) {
            incident.setDomainType(domainType);
        }
        if (hasText(eventId)) {
            incident.setEventId(eventId);
        }
        if (hasText(eventType)) {
            incident.setEventType(eventType);
        }
        if (hasText(requestId)) {
            incident.setRequestId(requestId);
        }
        if (hasText(trackingId) && !hasText(incident.getTrackingId())) {
            incident.setTrackingId(trackingId);
        }
        if (hasText(providerTrackingId) && !hasText(incident.getProviderTrackingId())) {
            incident.setProviderTrackingId(providerTrackingId);
        }
        if (hasText(reason) && !hasText(incident.getReason())) {
            incident.setReason(reason);
        }
        if (hasText(payloadDigest) && !hasText(incident.getPayloadDigest())) {
            incident.setPayloadDigest(payloadDigest);
        }
        if (hasText(rawPayload) && !hasText(incident.getRawPayload())) {
            incident.setRawPayload(rawPayload);
        }

        incident.setErrorMessage(appendErrorMessage(incident.getErrorMessage(), errorMessage));
        incident.setOccurrenceCount(incident.getOccurrenceCount() + 1);
        incident.setLastOccurredAt(now);
        incident.setUpdatedAt(now);
    }

    private String buildDedupeKey(
            String provider,
            WebhookIncidentType incidentType,
            String eventId,
            String requestId,
            String rawPayload
    ) {
        String providerKey = hasText(provider) ? provider : "UNKNOWN_PROVIDER";
        String typeKey = incidentType == null ? "UNCLASSIFIED_SYSTEM_EXCEPTION" : incidentType.name();

        if (hasText(requestId)) {
            return providerKey + ":REQ:" + requestId + ":" + typeKey;
        }
        if (hasText(eventId)) {
            return providerKey + ":EVT:" + eventId + ":" + typeKey;
        }
        return providerKey + ":PAYLOAD:" + digestPayload(rawPayload) + ":" + typeKey;
    }

    public String resolveDedupeKey(
            String provider,
            WebhookIncidentType incidentType,
            String eventId,
            String requestId,
            String rawPayload
    ) {
        return buildDedupeKey(provider, incidentType, eventId, requestId, rawPayload);
    }

    private String digestPayload(String rawPayload) {
        String value = hasText(rawPayload) ? rawPayload : "EMPTY_PAYLOAD";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available when digesting webhook incident payload");
            return Integer.toHexString(value.hashCode());
        }
    }

    private String appendErrorMessage(String current, String next) {
        if (!hasText(next)) {
            return current;
        }
        if (!hasText(current)) {
            return next;
        }
        return current + "\n" + next;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

