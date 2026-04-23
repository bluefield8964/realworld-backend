package realworld_backend.commerce.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stripeEvents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class StripeEvent {

    @Id
    private String eventId;

    @Column(nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    private StripeEventStatus status;

    private String lastError;
    private LocalDateTime eventHandledAt;

    private int attempts;
}

