package realworld_backend.commerce.service.core;

public class SessionStatusAntiCorruption {
    public static OrderStatus mapSessionStatus(String sessionStatus, String paymentStatus) {

        // Successful payment states
        if ("complete".equals(sessionStatus) && "paid".equals(paymentStatus)) {
            return OrderStatus.PAYMENT_SUCCESSFUL;
        }

        // In-progress payment states
        if ("open".equals(sessionStatus) && "unpaid".equals(paymentStatus)) {
            return OrderStatus.PAYMENT_PENDING;
        }

        // Failed or expired states
        if ("expired".equals(sessionStatus)) {
            return OrderStatus.PAYMENT_EXPIRED;
        }

        // Special case for free orders
        if ("complete".equals(sessionStatus) && "no_payment_required".equals(paymentStatus)) {
            return OrderStatus.FREE_ORDER_COMPLETED;
        }

        return OrderStatus.UNKNOWN;
    }
}

enum OrderStatus {
    PAYMENT_SUCCESSFUL,      // Payment completed
    PAYMENT_PENDING,         // Payment still pending
    PAYMENT_EXPIRED,         // Payment session expired
    PAYMENT_FAILED,          // Payment failed
    FREE_ORDER_COMPLETED,    // Free order completed
    UNKNOWN                  // Unknown state
}
