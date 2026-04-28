package realworld_backend.commerce.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Builder
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
    private Long productId;
    @NotNull
    private Long userId;
    @NotNull
    @Column(unique = true)
    private String orderNo;
    @NotNull
    private Long amount;
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(50)")
    private OrderStatus status;

    @Column(unique = true)
    private String sessionId;

    private String provider;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String paymentIntentId;
    @Column(length = 500)
    private String paymentUrl;
    @Column(unique = true)
    private String activeKey;

}

