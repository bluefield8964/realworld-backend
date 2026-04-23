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
    private String sku;                  // 娑撴艾濮熼崬顖欑缂傛牜鐖滈敍灞筋洤 VIP_MONTHLY

    @Column(nullable = false, length = 120)
    private String name;                 // 鐏炴洜銇氶崥?

    @Column(length = 500)
    private String description;          // 閹诲繗鍫?

    @Column(nullable = false)
    private Long priceAmount;            // 闁叉垿顤傞敍鍫熸付鐏忓繗鎻ｇ敮浣稿礋娴ｅ稄绱濇俊鍌氬瀻閿?

    @Column(nullable = false, length = 10)
    private String currency;             // usd / twd ...

    @Column(nullable = false)
    private Boolean active;              // 閺勵垰鎯侀崣顖氭暛

    @Column(nullable = false)
    private Integer sortOrder;           // 閸掓銆冮幒鎺戠碍

    @Column(length = 128)
    private String stripePriceId;        // Stripe Price ID閿涘牊甯归懡鎰摠鏉╂瑤閲滈敍?

}

