package realworld_backend.commerce.service.statemachine;

import org.springframework.stereotype.Service;
import realworld_backend.commerce.event.BusinessEventType;
import realworld_backend.commerce.model.OrderStatus;
import realworld_backend.commerce.model.PaymentStatus;

import java.util.EnumSet;

/**
 * Centralizes order/payment webhook transition semantics.
 * This service only classifies transitions and does not write state.
 */
@Service
public class OrderWebhookStateMachine {

    public OrderWebhookStateDecision evaluate(
            BusinessEventType eventType,
            OrderStatus orderStatus,
            PaymentStatus paymentStatus
    ) {
        if (eventType == null || orderStatus == null || paymentStatus == null) {
            return illegal(eventType, orderStatus, paymentStatus, "missing current state");
        }

        return switch (eventType) {
            case CHECKOUT_SESSION_COMPLETED -> evaluateCompleted(orderStatus, paymentStatus);
            case CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED -> evaluateAsyncPaymentFailed(orderStatus, paymentStatus);
            case CHECKOUT_SESSION_EXPIRED -> evaluateExpired(orderStatus, paymentStatus);
            default -> ignore(eventType, orderStatus, paymentStatus, "event not handled by order state machine");
        };
    }

    private OrderWebhookStateDecision evaluateCompleted(OrderStatus orderStatus, PaymentStatus paymentStatus) {
        if (orderStatus == OrderStatus.PAID && paymentStatus == PaymentStatus.SUCCESS) {
            return ignore(BusinessEventType.CHECKOUT_SESSION_COMPLETED, orderStatus, paymentStatus, "already paid");
        }
        if (EnumSet.of(OrderStatus.CREATED, OrderStatus.PENDING, OrderStatus.RECONCILING, OrderStatus.PAYMENT_FAILED_RETRYABLE)
                .contains(orderStatus)
                && EnumSet.of(PaymentStatus.INIT, PaymentStatus.PROCESSING, PaymentStatus.PAYING, PaymentStatus.FAILED)
                .contains(paymentStatus)) {
            return legal(
                    BusinessEventType.CHECKOUT_SESSION_COMPLETED,
                    orderStatus,
                    paymentStatus,
                    OrderStatus.PAID,
                    PaymentStatus.SUCCESS,
                    "payment completed"
            );
        }
        if (orderStatus == OrderStatus.FAILED || orderStatus == OrderStatus.CANCELLED) {
            return retryableIllegal(BusinessEventType.CHECKOUT_SESSION_COMPLETED, orderStatus, paymentStatus,
                    "completed arrived after terminal order state");
        }
        return illegal(BusinessEventType.CHECKOUT_SESSION_COMPLETED, orderStatus, paymentStatus,
                "completed does not match current order/payment state");
    }

    private OrderWebhookStateDecision evaluateAsyncPaymentFailed(OrderStatus orderStatus, PaymentStatus paymentStatus) {
        if (orderStatus == OrderStatus.PAID && paymentStatus == PaymentStatus.SUCCESS) {
            return ignore(BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED, orderStatus, paymentStatus,
                    "late failure after success");
        }
        if (orderStatus == OrderStatus.PAYMENT_FAILED_RETRYABLE && paymentStatus == PaymentStatus.FAILED) {
            return ignore(BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED, orderStatus, paymentStatus,
                    "already retryable failed");
        }
        if (EnumSet.of(OrderStatus.CREATED, OrderStatus.PENDING, OrderStatus.RECONCILING)
                .contains(orderStatus)
                && EnumSet.of(PaymentStatus.INIT, PaymentStatus.PROCESSING, PaymentStatus.PAYING)
                .contains(paymentStatus)) {
            return legal(
                    BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED,
                    orderStatus,
                    paymentStatus,
                    OrderStatus.PAYMENT_FAILED_RETRYABLE,
                    PaymentStatus.FAILED,
                    "async payment failed"
            );
        }
        return illegal(BusinessEventType.CHECKOUT_SESSION_ASYNC_PAYMENT_FAILED, orderStatus, paymentStatus,
                "async payment failed does not match current state");
    }

    private OrderWebhookStateDecision evaluateExpired(OrderStatus orderStatus, PaymentStatus paymentStatus) {
        if (orderStatus == OrderStatus.PAID && paymentStatus == PaymentStatus.SUCCESS) {
            return ignore(BusinessEventType.CHECKOUT_SESSION_EXPIRED, orderStatus, paymentStatus,
                    "late expired after success");
        }
        if (orderStatus == OrderStatus.FAILED && paymentStatus == PaymentStatus.FAILED) {
            return ignore(BusinessEventType.CHECKOUT_SESSION_EXPIRED, orderStatus, paymentStatus,
                    "already expired or failed");
        }
        if (EnumSet.of(OrderStatus.CREATED, OrderStatus.PENDING, OrderStatus.RECONCILING, OrderStatus.PAYMENT_FAILED_RETRYABLE)
                .contains(orderStatus)
                && EnumSet.of(PaymentStatus.INIT, PaymentStatus.PROCESSING, PaymentStatus.PAYING, PaymentStatus.FAILED)
                .contains(paymentStatus)) {
            return legal(
                    BusinessEventType.CHECKOUT_SESSION_EXPIRED,
                    orderStatus,
                    paymentStatus,
                    OrderStatus.FAILED,
                    PaymentStatus.FAILED,
                    "checkout session expired"
            );
        }
        return illegal(BusinessEventType.CHECKOUT_SESSION_EXPIRED, orderStatus, paymentStatus,
                "expired does not match current state");
    }

    private OrderWebhookStateDecision legal(
            BusinessEventType eventType,
            OrderStatus currentOrderStatus,
            PaymentStatus currentPaymentStatus,
            OrderStatus nextOrderStatus,
            PaymentStatus nextPaymentStatus,
            String reason
    ) {
        return new OrderWebhookStateDecision(
                eventType,
                currentOrderStatus,
                currentPaymentStatus,
                nextOrderStatus,
                nextPaymentStatus,
                orderNatureOf(currentOrderStatus),
                orderNatureOf(nextOrderStatus),
                paymentNatureOf(currentPaymentStatus),
                paymentNatureOf(nextPaymentStatus),
                TransitionClass.LEGAL_TRANSITION,
                true,
                false,
                reason
        );
    }

    private OrderWebhookStateDecision retryableIllegal(
            BusinessEventType eventType,
            OrderStatus currentOrderStatus,
            PaymentStatus currentPaymentStatus,
            String reason
    ) {
        return new OrderWebhookStateDecision(
                eventType,
                currentOrderStatus,
                currentPaymentStatus,
                currentOrderStatus,
                currentPaymentStatus,
                orderNatureOf(currentOrderStatus),
                orderNatureOf(currentOrderStatus),
                paymentNatureOf(currentPaymentStatus),
                paymentNatureOf(currentPaymentStatus),
                TransitionClass.RETRYABLE_ILLEGAL_TRANSITION,
                false,
                true,
                reason
        );
    }

    private OrderWebhookStateDecision illegal(
            BusinessEventType eventType,
            OrderStatus currentOrderStatus,
            PaymentStatus currentPaymentStatus,
            String reason
    ) {
        return new OrderWebhookStateDecision(
                eventType,
                currentOrderStatus,
                currentPaymentStatus,
                currentOrderStatus,
                currentPaymentStatus,
                orderNatureOf(currentOrderStatus),
                orderNatureOf(currentOrderStatus),
                paymentNatureOf(currentPaymentStatus),
                paymentNatureOf(currentPaymentStatus),
                TransitionClass.ILLEGAL_TRANSITION,
                false,
                true,
                reason
        );
    }

    private OrderWebhookStateDecision ignore(
            BusinessEventType eventType,
            OrderStatus currentOrderStatus,
            PaymentStatus currentPaymentStatus,
            String reason
    ) {
        return new OrderWebhookStateDecision(
                eventType,
                currentOrderStatus,
                currentPaymentStatus,
                currentOrderStatus,
                currentPaymentStatus,
                orderNatureOf(currentOrderStatus),
                orderNatureOf(currentOrderStatus),
                paymentNatureOf(currentPaymentStatus),
                paymentNatureOf(currentPaymentStatus),
                TransitionClass.IGNORE_TRANSITION,
                false,
                false,
                reason
        );
    }

    public StateNature orderNatureOf(OrderStatus status) {
        if (status == null) {
            return StateNature.INTERMEDIATE_STATE;
        }
        return switch (status) {
            case PAID, FAILED, CANCELLED -> StateNature.TERMINAL_STATE;
            case PAYMENT_FAILED_RETRYABLE, RECONCILING -> StateNature.RECOVERABLE_STATE;
            case CREATED, PENDING -> StateNature.INTERMEDIATE_STATE;
        };
    }

    public StateNature paymentNatureOf(PaymentStatus status) {
        if (status == null) {
            return StateNature.INTERMEDIATE_STATE;
        }
        return switch (status) {
            case SUCCESS, FAILED -> StateNature.TERMINAL_STATE;
            case INIT, PROCESSING, PAYING -> StateNature.INTERMEDIATE_STATE;
        };
    }
}
