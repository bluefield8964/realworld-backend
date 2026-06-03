package realworld_backend.commerce.model;


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
    private String sku;                  // Stable business SKU (example: VIP_MONTHLY)

    @Column(nullable = false, length = 120)
    private String name;                 // Display name

    @Column(length = 500)
    private String description;          // Product description

    @Column(nullable = false)
    private Long priceAmount;            // Price in the smallest currency unit

    @Column(nullable = false, length = 10)
    private String currency;             // Currency code (usd, twd, ...)

    @Column(nullable = false)
    private Boolean active;              // Whether the product is purchasable

    @Column(nullable = false)
    private Integer sortOrder;           // UI display order

    @Column(length = 128)
    private String stripePriceId;        // Stripe Price ID bound to this product

}
