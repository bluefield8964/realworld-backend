package realworld_backend.model.commerceModule;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
@Builder
@Entity
@Table(name = "abnormalOrders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AbnormalOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String provider;
    private String orderNo;
    private String eventId;
    private String eventType;

    @Column(unique = true)
    private String sessionId;

    @Column(unique = true)
    private String requestId;
    private String rawPayload;
    private String reason;
    private String errorMessage;
    private int retryCount;
    @NotNull
    @Enumerated(EnumType.STRING)
    private AbnormalOrderStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private LocalDateTime lastRetryAt;
    private LocalDateTime nextRetryAt;
    private LocalDateTime handledAt;
    private String remark;


}
