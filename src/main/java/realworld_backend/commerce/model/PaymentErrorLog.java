package realworld_backend.commerce.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "PaymentErrorLogs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentErrorLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String requestId;
    private String provider;
    private String errorMessage;
    private String errorCode;
    private LocalDateTime timestamp;
}

