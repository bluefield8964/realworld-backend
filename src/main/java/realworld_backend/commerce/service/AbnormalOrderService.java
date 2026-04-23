package realworld_backend.commerce.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.auth.model.User;
import realworld_backend.commerce.model.*;
import realworld_backend.commerce.model.core.ProviderSession;
import realworld_backend.commerce.repository.AbnormalOrderRepository;
import realworld_backend.commerce.service.core.PaymentChannel;
import realworld_backend.commerce.service.core.PaymentChannelException;
import realworld_backend.commerce.service.core.PaymentChannelRouter;
import realworld_backend.auth.service.UserService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@EnableScheduling
@Service
@RequiredArgsConstructor
/**
 * Abnormal-order reconciliation service.
 * Second-line reconcile path for terminal or retry-exhausted events.
 */
public class AbnormalOrderService {
    // Base backoff window for next-retry scheduling.
    private final AbnormalOrderRepository abnormalOrderRepository;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ProductItemService productService;
    private final UserService userService;
    private final Duration PROCESSING_STALE = Duration.ofSeconds(30);
    private final RedissonClient redissonClient;

    private final PaymentChannelRouter paymentChannelRouter;

    /**
     * Upsert abnormal row by sessionId.
     * REQUIRES_NEW keeps trace even if outer transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertAbnormalOrder(
            String orderNo,
            String eventId,
            String eventType,
            String sessionId,
            String reason,
            String errorMessage,
            AbnormalOrderStatus status,
            String provider,
            String requestId
    ) {
        LocalDateTime now = LocalDateTime.now();
        String errorLine = "[" + now + "] " + errorMessage;

        abnormalOrderRepository.upsertBySessionId(
                orderNo,
                eventId,
                eventType,
                sessionId,
                requestId,
                provider,
                reason,
                errorLine,
                status.name(),
                now,   // createdAt
                now,   // lastRetryAt
                now    // nextRetryAt
        );
    }

    /**
     * Batch reconcile entry, typically triggered by scheduler.
     */
    public void retryAbnormalOrder() {
        List<AbnormalOrder> retryCandidates = abnormalOrderRepository.findRetryCandidates
                (LocalDateTime.now(), PageRequest.of(0, 100));
        for (AbnormalOrder retryCandidate : retryCandidates) {
            String sessionId = retryCandidate.getSessionId();
            RLock lock = redissonClient.getLock("AbnormalOrder:lock:" + sessionId);
            try {
                boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
                if (!locked) {
                    log.warn("Failed to acquire lock for session {}, another thread is processing", sessionId);
                    continue;
                }
                PaymentChannel paymentChannel = paymentChannelRouter.get(retryCandidate.getProvider());
                ProviderSession providerSession = paymentChannel.retrieveSession(sessionId);
                checkSession(providerSession, retryCandidate);

            } catch (PaymentChannelException e) {
                // Record item-level Stripe error and continue batch.
                retryCandidate.setRetryCount(retryCandidate.getRetryCount() + 1);
                retryCandidate.setLastRetryAt(LocalDateTime.now());
                retryCandidate.setNextRetryAt(
                        LocalDateTime.now()
                                .plus(PROCESSING_STALE.multipliedBy(retryCandidate.getRetryCount())));
                retryCandidate.setUpdatedAt(LocalDateTime.now());
                retryCandidate.setErrorMessage("stripe_retrieve_failed: " + e.getMessage());

                if (retryCandidate.getRetryCount() >= 5) {
                    retryCandidate.setStatus(AbnormalOrderStatus.RETRY_EXHAUSTED);
                }

                abnormalOrderRepository.save(retryCandidate);
                continue; // continue handle next abnormalOrder
            } catch (Exception e) {
                // Never stop the whole batch for one record failure.
                retryCandidate.setRetryCount(retryCandidate.getRetryCount() + 1);
                retryCandidate.setLastRetryAt(LocalDateTime.now());
                retryCandidate.setNextRetryAt(
                        LocalDateTime.now()
                                .plus(PROCESSING_STALE.multipliedBy(retryCandidate.getRetryCount())));
                retryCandidate.setUpdatedAt(LocalDateTime.now());
                retryCandidate.setErrorMessage("reconcile_failed: " + e.getMessage());
                abnormalOrderRepository.save(retryCandidate);
                continue;
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }

        }

    }

    private void checkSession(ProviderSession retrieve, AbnormalOrder retryCandidate) {
        // 1) Stripe status is the source of truth.
        if (!"paid".equals(retrieve.getPaymentStatus()) ||
                !"complete".equals(retrieve.getStatus())) {
            // Not paid/complete: stop reconcile and mark outcome.
            retryCandidate.setStatus(AbnormalOrderStatus.UNPAID_CONFIRMED);
            retryCandidate.setHandledAt(LocalDateTime.now());
            retryCandidate.setLastRetryAt(LocalDateTime.now());
            retryCandidate.setErrorMessage("order status is not paid or complete");
            retryCandidate.setUpdatedAt(LocalDateTime.now());
            abnormalOrderRepository.save(retryCandidate);
            return;
        }
        String sessionId = retrieve.getId();

        if (abnormalOrderRepository.existsBySessionIdAndStatus(sessionId, AbnormalOrderStatus.RE_PENDING)) {
            // Another worker is already reconciling this session.
            return;
        }
        // Mark as RE_PENDING before starting local repair.
        abnormalOrderRepository.updateStatusBySessionId(sessionId, AbnormalOrderStatus.RE_PENDING, LocalDateTime.now());

        // 2) Build reconcile context from Stripe metadata.
        Map<String, String> metadata = retrieve.getMetadata();
        if (metadata == null) {
            retryCandidate.setRetryCount(retryCandidate.getRetryCount() + 1);
            retryCandidate.setLastRetryAt(LocalDateTime.now());
            retryCandidate.setNextRetryAt(
                    LocalDateTime.now()
                            .plus(PROCESSING_STALE.multipliedBy(retryCandidate.getRetryCount())));
            retryCandidate.setUpdatedAt(LocalDateTime.now());
            retryCandidate.setErrorMessage
                    ("metadata missing: stripe metadata {}");
            abnormalOrderRepository.save(retryCandidate);
            return;}
            String orderNo = metadata.get("orderNo");
            Long userId = Long.valueOf(metadata.get("userId"));
            String productId = metadata.get("product");
            Long created = retrieve.getCreated();
            Long amount = retrieve.getAmountTotal();

            // 3) Validate metadata consistency before patching local rows.
            Product product = productService.findByProductId(Long.valueOf(productId));
            Long price = product.getPriceAmount();
            if (!price.equals(amount)) {
                // Price mismatch is treated as retryable for now.
                retryCandidate.setRetryCount(retryCandidate.getRetryCount() + 1);
                retryCandidate.setLastRetryAt(LocalDateTime.now());
                retryCandidate.setNextRetryAt(
                        LocalDateTime.now()
                                .plus(PROCESSING_STALE.multipliedBy(retryCandidate.getRetryCount())));
                retryCandidate.setUpdatedAt(LocalDateTime.now());
                retryCandidate.setErrorMessage
                        ("price mismatch: stripe amount {" + amount + "} product amount {" + price + "}");
                abnormalOrderRepository.save(retryCandidate);
                return;
            }
            Optional<User> user = userService.findByUserId(userId);
            if (user.isEmpty()) {
                // Missing user is treated as retryable for now.
                retryCandidate.setRetryCount(retryCandidate.getRetryCount() + 1);
                retryCandidate.setLastRetryAt(LocalDateTime.now());
                retryCandidate.setNextRetryAt(
                        LocalDateTime.now()
                                .plus(PROCESSING_STALE.multipliedBy(retryCandidate.getRetryCount())));
                retryCandidate.setUpdatedAt(LocalDateTime.now());
                retryCandidate.setErrorMessage
                        ("user mismatch: stripe userId {" + userId + "} not found in local db");
                abnormalOrderRepository.save(retryCandidate);
                return;
            }

            // 4) Apply status-specific repair strategy.
            Boolean ifFixed = checkAndFixOrderAndPayment(retryCandidate, orderNo, sessionId
                    , Long.valueOf(productId), userId, created, amount);
            if (ifFixed) {
                retryCandidate.setStatus(AbnormalOrderStatus.FIXED);
                retryCandidate.setHandledAt(LocalDateTime.now());
                retryCandidate.setLastRetryAt(LocalDateTime.now());
                retryCandidate.setUpdatedAt(LocalDateTime.now());
                abnormalOrderRepository.save(retryCandidate);
            } else {
                if (retryCandidate.getRetryCount() <= 5) {
                }
                retryCandidate.setStatus(AbnormalOrderStatus.UNPAID_CONFIRMED);
                retryCandidate.setLastRetryAt(LocalDateTime.now());
                retryCandidate.setErrorMessage("unknown");
                retryCandidate.setUpdatedAt(LocalDateTime.now());
                retryCandidate.setRetryCount(retryCandidate.getRetryCount() + 1);
                retryCandidate.setNextRetryAt(LocalDateTime.now()
                        .plus(PROCESSING_STALE.multipliedBy(retryCandidate.getRetryCount())));
                abnormalOrderRepository.save(retryCandidate);


            }
        }

        /**
         * Repair by abnormal status type.
         */
        private Boolean checkAndFixOrderAndPayment (AbnormalOrder retryCandidate,
                String orderNo, String sessionId,
                Long productId, Long customer,
                Long created, Long amount){
            AbnormalOrderStatus status = retryCandidate.getStatus();


            if (status.equals(AbnormalOrderStatus.ORDER_MISSING)) {

                Order order = Order.builder().orderNo(orderNo)
                        .productId(productId)
                        .stripeSessionId(sessionId)
                        .userId(customer)
                        .amount(amount)
                        .updatedAt(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .status(OrderStatus.PAID)
                        .build();
                orderService.saveReconcileOrder(order);
                return true;
            } else if (status.equals(AbnormalOrderStatus.PAYMENT_MISSING)) {
                Payment payment = Payment.builder()
                        .orderNo(orderNo)
                        .status(PaymentStatus.SUCCESS)
                        .provider("stripe")
                        .sessionId(sessionId).build();
                paymentService.saveReconcilePayment(payment);
                return true;
            } else if (status.equals(AbnormalOrderStatus.RETRY_EXHAUSTED)) {
                Order order = orderService.findBySessionId(sessionId);
                Payment payment = paymentService.findBySessionId(sessionId);
                if (order == null || payment == null) {
                    retryCandidate.setStatus(AbnormalOrderStatus.MANUAL_REVIEW);
                    retryCandidate.setErrorMessage("reconcile retry_exhausted but order/payment missing");
                    retryCandidate.setUpdatedAt(LocalDateTime.now());
                    retryCandidate.setLastRetryAt(LocalDateTime.now());
                    abnormalOrderRepository.save(retryCandidate);
                    return false;
                }
                order.setStatus(OrderStatus.PAID);
                order.setUpdatedAt(LocalDateTime.now());
                order.setProductId(productId);
                order.setUserId(customer);
                order.setAmount(amount);
                order.setOrderNo(orderNo);

                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setProvider("stripe");
                payment.setSessionId(sessionId);
                payment.setOrderNo(orderNo);
                orderService.saveReconcileOrder(order);
                paymentService.saveReconcilePayment(payment);

                return true;
            }
            return false;


        }


    }


