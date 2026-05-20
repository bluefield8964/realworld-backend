package realworld_backend.commerce.service.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.article.model.UserProfile;
import realworld_backend.article.service.UserService;
import realworld_backend.commerce.model.*;
import realworld_backend.commerce.model.core.ProviderSession;
import realworld_backend.commerce.model.log.AbnormalOrder;
import realworld_backend.commerce.model.log.AbnormalOrderStatus;
import realworld_backend.commerce.model.log.AbnormalOrderType;
import realworld_backend.commerce.repository.AbnormalOrderRepository;
import realworld_backend.commerce.service.PaymentService;
import realworld_backend.commerce.service.ProductItemService;
import realworld_backend.commerce.service.core.PaymentChannel;
import realworld_backend.commerce.service.core.PaymentChannelException;
import realworld_backend.commerce.service.core.PaymentChannelRouter;
import realworld_backend.common.exception.BizException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderAbnormalService {
    private static final int MAX_RETRY_COUNT = 5;
    private static final Duration RECONCILING_LEASE_TIMEOUT = Duration.ofMinutes(3);
    private final Duration processingStale = Duration.ofSeconds(30);

    private final AbnormalOrderRepository abnormalOrderRepository;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ProductItemService productService;
    private final UserService userService;
    private final PaymentChannelRouter paymentChannelRouter;

    public void reconcile(AbnormalOrder retryCandidate) throws PaymentChannelException {
        String sessionId = retryCandidate.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            scheduleRetry(retryCandidate, "order sessionId missing");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int claimed = abnormalOrderRepository.updateStatusToReconcilingBySessionId(
                sessionId,
                AbnormalOrderStatus.RECONCILING,
                now,
                now.minus(RECONCILING_LEASE_TIMEOUT)
        );
        if (claimed == 0) {
            return;
        }

        PaymentChannel paymentChannel = paymentChannelRouter.get(retryCandidate.getProvider());
        ProviderSession providerSession = paymentChannel.retrieveSession(sessionId);
        checkOrderSession(providerSession, retryCandidate);
    }

    private void checkOrderSession(ProviderSession retrieve, AbnormalOrder retryCandidate) {
        if (retrieve == null) {
            scheduleRetry(retryCandidate, "provider session retrieve returned null");
            return;
        }
        if (!"paid".equals(retrieve.getPaymentStatus()) || !"complete".equals(retrieve.getStatus())) {
            retryCandidate.setStatus(AbnormalOrderStatus.UNPAID_CONFIRMED);
            retryCandidate.setHandledAt(LocalDateTime.now());
            retryCandidate.setLastRetryAt(LocalDateTime.now());
            retryCandidate.setErrorMessage("order status is not paid or complete");
            retryCandidate.setUpdatedAt(LocalDateTime.now());
            abnormalOrderRepository.save(retryCandidate);
            return;
        }

        Map<String, String> metadata = retrieve.getMetadata();
        if (metadata == null) {
            markManualReview(retryCandidate, "provider metadata missing");
            return;
        }
        String sessionId = retrieve.getId();
        String orderNo = metadata.get("orderNo");
        if (!hasText(orderNo)) {
            markManualReview(retryCandidate, "provider metadata orderNo missing");
            return;
        }
        Long userId = parseLongOrManualReview(retryCandidate, metadata.get("userId"), "userId");
        if (userId == null) {
            return;
        }
        Long productId = parseLongOrManualReview(retryCandidate, metadata.get("product"), "product");
        if (productId == null) {
            return;
        }
        Long amount = retrieve.getAmountTotal();
        if (amount == null || amount < 0) {
            markManualReview(retryCandidate, "provider amount invalid: " + amount);
            return;
        }

        Product product;
        try {
            product = productService.findByProductId(productId);
        } catch (BizException ex) {
            markManualReview(retryCandidate, "local product resolution failed: productId=" + productId);
            return;
        }
        if (product == null || product.getPriceAmount() == null) {
            markManualReview(retryCandidate, "local product missing or price missing: productId=" + productId);
            return;
        }
        Long price = product.getPriceAmount();
        if (!Objects.equals(price, amount)) {
            markManualReview(retryCandidate, "price mismatch: provider amount {" + amount + "} product amount {" + price + "}");
            return;
        }

        Optional<UserProfile> user = userService.findByUserId(userId);
        if (user.isEmpty()) {
            markManualReview(retryCandidate, "user mismatch: provider userId {" + userId + "} not found in local db");
            return;
        }

        boolean fixed = checkAndFixOrderAndPayment(retryCandidate, orderNo, sessionId, productId, userId, amount);
        if (fixed) {
            markFixed(retryCandidate, "order_reconciled");
            return;
        }

        retryCandidate.setStatus(AbnormalOrderStatus.UNPAID_CONFIRMED);
        retryCandidate.setLastRetryAt(LocalDateTime.now());
        retryCandidate.setErrorMessage("unknown");
        retryCandidate.setUpdatedAt(LocalDateTime.now());
        retryCandidate.setRetryCount(retryCandidate.getRetryCount() + 1);
        retryCandidate.setNextRetryAt(LocalDateTime.now()
                .plus(processingStale.multipliedBy(retryCandidate.getRetryCount())));
        abnormalOrderRepository.save(retryCandidate);
    }

    private boolean checkAndFixOrderAndPayment(AbnormalOrder retryCandidate,
                                               String orderNo,
                                               String sessionId,
                                               Long productId,
                                               Long customer,
                                               Long amount) {
        AbnormalOrderType abnormalType = resolveAbnormalType(retryCandidate);
        if (abnormalType == AbnormalOrderType.ORDER_MISSING) {
            Order order = Order.builder()
                    .orderNo(orderNo)
                    .productId(productId)
                    .sessionId(sessionId)
                    .userId(customer)
                    .amount(amount)
                    .updatedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .status(OrderStatus.PAID)
                    .build();
            orderService.saveReconcileOrder(order);
            return true;
        }

        if (abnormalType == AbnormalOrderType.PAYMENT_MISSING) {
            Payment payment = Payment.builder()
                    .orderNo(orderNo)
                    .status(PaymentStatus.SUCCESS)
                    .provider("STRIPE")
                    .sessionId(sessionId)
                    .build();
            paymentService.saveReconcilePayment(payment);
            return true;
        }

        if (abnormalType == AbnormalOrderType.EVENT_RETRY_BUDGET_EXHAUSTED
                || retryCandidate.getStatus() == AbnormalOrderStatus.EXHAUSTED
                || retryCandidate.getStatus() == AbnormalOrderStatus.RETRY_EXHAUSTED) {
            Order order = orderService.findBySessionId(sessionId);
            Payment payment = paymentService.findBySessionId(sessionId);
            if (order == null || payment == null) {
                markManualReview(retryCandidate, "reconcile retry_exhausted but order/payment missing");
                return false;
            }

            order.setStatus(OrderStatus.PAID);
            order.setActiveKey(null);
            order.setUpdatedAt(LocalDateTime.now());
            order.setProductId(productId);
            order.setUserId(customer);
            order.setAmount(amount);
            order.setOrderNo(orderNo);

            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setProvider("STRIPE");
            payment.setSessionId(sessionId);
            payment.setOrderNo(orderNo);
            orderService.saveReconcileOrder(order);
            paymentService.saveReconcilePayment(payment);
            return true;
        }

        return false;
    }

    private void scheduleRetry(AbnormalOrder retryCandidate, String message) {
        int nextRetryCount = retryCandidate.getRetryCount() + 1;
        LocalDateTime now = LocalDateTime.now();
        retryCandidate.setRetryCount(nextRetryCount);
        retryCandidate.setLastRetryAt(now);
        retryCandidate.setUpdatedAt(now);
        retryCandidate.setErrorMessage(appendErrorMessage(retryCandidate.getErrorMessage(), message));
        retryCandidate.setNextRetryAt(now.plus(processingStale.multipliedBy(nextRetryCount)));
        if (nextRetryCount >= MAX_RETRY_COUNT) {
            retryCandidate.setStatus(AbnormalOrderStatus.EXHAUSTED);
        } else {
            retryCandidate.setStatus(AbnormalOrderStatus.PENDING);
        }
        abnormalOrderRepository.save(retryCandidate);
    }

    private void markManualReview(AbnormalOrder retryCandidate, String message) {
        LocalDateTime now = LocalDateTime.now();
        retryCandidate.setStatus(AbnormalOrderStatus.MANUAL_REVIEW);
        retryCandidate.setUpdatedAt(now);
        retryCandidate.setLastRetryAt(now);
        retryCandidate.setHandledAt(now);
        retryCandidate.setErrorMessage(appendErrorMessage(retryCandidate.getErrorMessage(), message));
        abnormalOrderRepository.save(retryCandidate);
    }

    private void markFixed(AbnormalOrder retryCandidate, String message) {
        LocalDateTime now = LocalDateTime.now();
        retryCandidate.setStatus(AbnormalOrderStatus.FIXED);
        retryCandidate.setUpdatedAt(now);
        retryCandidate.setLastRetryAt(now);
        retryCandidate.setHandledAt(now);
        retryCandidate.setErrorMessage(appendErrorMessage(retryCandidate.getErrorMessage(), message));
        abnormalOrderRepository.save(retryCandidate);
    }

    private String appendErrorMessage(String current, String next) {
        if (current == null || current.isBlank()) {
            return next;
        }
        return current + "\n" + next;
    }

    private Long parseLongOrManualReview(AbnormalOrder retryCandidate, String raw, String fieldName) {
        if (!hasText(raw)) {
            markManualReview(retryCandidate, "provider metadata " + fieldName + " missing");
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            markManualReview(retryCandidate, "provider metadata " + fieldName + " invalid: " + raw);
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private AbnormalOrderType resolveAbnormalType(AbnormalOrder retryCandidate) {
        if (retryCandidate.getAbnormalType() != null) {
            return retryCandidate.getAbnormalType();
        }
        AbnormalOrderStatus legacyStatus = retryCandidate.getStatus();
        AbnormalOrderType resolved = switch (legacyStatus) {
            case ORDER_MISSING -> AbnormalOrderType.ORDER_MISSING;
            case PAYMENT_MISSING -> AbnormalOrderType.PAYMENT_MISSING;
            case RETRY_EXHAUSTED -> AbnormalOrderType.EVENT_RETRY_BUDGET_EXHAUSTED;
            default -> null;
        };
        if (resolved != null) {
            retryCandidate.setAbnormalType(resolved);
        }
        return resolved;
    }
}
