package realworld_backend.commerce.model;

public enum AbnormalOrderStatus {
    FIXED,                // 瀹歌弓鎱ㄦ径宥忕礄閺堫剙婀寸拋銏犲礋瀹告彃鎷癝tripe閸氬本顒為敍?
    UNPAID_CONFIRMED,     // 瀹歌尙鈥樼拋銈嗘弓閺€顖欑帛/閺€顖欑帛婢惰精瑙?
    RETRY_EXHAUSTED,      // 閼奉亜濮╅柌宥堢槸濞嗏剝鏆熷鑼暏鐏?
    ORDER_MISSING,
    PAYMENT_MISSING,
    RE_PENDING,           // abnormal order is re processing
    MANUAL_REVIEW         //need manual review
}

