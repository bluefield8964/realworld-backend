package realworld_backend.commerce.model.core;

import com.stripe.model.Event;
import lombok.Builder;
import lombok.Data;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.service.core.EventAnticorruptionLayer;
import realworld_backend.commerce.service.core.ProviderTimeMapper;

import java.time.Instant;
import java.time.LocalDateTime;

@Builder
@Data
public class ProviderRawEvent {
    // checkout.session.*
    // ===== event envelope =====
    private String provider;      // stripe
    private String eventId;
    private BusinessEventType type;          // eventType
    private Long created;
    private Boolean livemode;
    private String rawType;
    private String rawObjectJson; // data.object.toJson()
    private transient Instant createdAt;

    public Instant createdAtInstant() {
        if (createdAt == null) {
            createdAt = ProviderTimeMapper.toInstant(created);
        }
        return createdAt;
    }

    public LocalDateTime createdAtUtc() {
        return ProviderTimeMapper.toUtcLocalDateTime(createdAtInstant());
    }

    public static ProviderRawEvent fromProviderEvent(Event event, String provider) {
        String json = event.getData().getObject().toJson();
        BusinessEventType eventType = EventAnticorruptionLayer.convertStripeEvent(event.getType(), json);

        return ProviderRawEvent.builder()
                .provider(provider)
                .eventId(event.getId())
                .type(eventType)
                .rawObjectJson(json)
                .rawType(event.getType())
                .created(event.getCreated())
                .createdAt(ProviderTimeMapper.toInstant(event.getCreated()))
                .livemode(event.getLivemode())
                .build();
    }
}

