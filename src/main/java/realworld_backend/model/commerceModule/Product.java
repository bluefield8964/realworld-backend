package realworld_backend.model.commerceModule;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String sku;                  // 业务唯一编码，如 VIP_MONTHLY

    @Column(nullable = false, length = 120)
    private String name;                 // 展示名

    @Column(length = 500)
    private String description;          // 描述

    @Column(nullable = false)
    private Long priceAmount;            // 金额（最小货币单位，如分）

    @Column(nullable = false, length = 10)
    private String currency;             // usd / twd ...

    @Column(nullable = false)
    private Boolean active;              // 是否可售

    @Column(nullable = false)
    private Integer sortOrder;           // 列表排序

    @Column(length = 128)
    private String stripePriceId;        // Stripe Price ID（推荐存这个）

}
