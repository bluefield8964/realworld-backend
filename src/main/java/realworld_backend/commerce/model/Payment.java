package realworld_backend.commerce.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull
    private String orderNo;
    @NotNull
    private String provider;        // paypal / stripe
    @Column(unique = true)
    private String sessionId;   // from provider
    @NotNull
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;


    //for exception
    @Column(length = 500)
    private String errorMsg;

    private String requestId;

    private String code;
}

