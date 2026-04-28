package realworld_backend.commerce.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import realworld_backend.commerce.event.BusinessEventType;

import java.time.LocalDateTime;

@Entity
@Table(name = "business_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class BusinessEvent {

    @Id
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,columnDefinition = "VARCHAR(50)")
    private BusinessEventType type;

    @Column(columnDefinition = "VARCHAR(50)")
    @Enumerated(EnumType.STRING)
    private EventStatus status;

    private String lastError;
    private LocalDateTime eventHandledAt;

    private int attempts;
}

