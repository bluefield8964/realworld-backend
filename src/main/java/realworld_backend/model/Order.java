package realworld_backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull
    private Long product;
    @NotNull
    private Long userId;
    @NotNull
    @Column(unique = true)
    private String orderNo;
    @NotNull
    private Long amount;
    @NotNull
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    @NotNull
    @Column(unique = true)
    private String stripeSessionId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String stripePaymentIntentId;
    @Column(length = 500)
    private String paymentUrl;

    private String activeKey;

}
