package realworld_backend.model.commerceModule;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stripeEvents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StripeEvent {

    @Id
    private String eventId;

    @Column(nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    private StripeEventStatus status;

    private String lastError;

    @Column(nullable = false)
    private LocalDateTime eventHandledAt;

    private int attempts;
}
