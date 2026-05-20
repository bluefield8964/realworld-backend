package realworld_backend.commerce.service.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.model.log.AbnormalOrder;
import realworld_backend.commerce.model.log.AbnormalOrderStatus;
import realworld_backend.commerce.model.core.ProviderInvoice;
import realworld_backend.commerce.model.core.ProviderSubscription;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.repository.AbnormalOrderRepository;
import realworld_backend.commerce.service.InvoiceService;
import realworld_backend.commerce.service.core.PaymentChannel;
import realworld_backend.commerce.service.core.PaymentChannelRouter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SubscriptionAbnormalService {
    private static final int MAX_RETRY_COUNT = 5;
    private static final Duration RECONCILING_LEASE_TIMEOUT = Duration.ofMinutes(3);
    private final Duration processingStale = Duration.ofSeconds(30);

    private final AbnormalOrderRepository abnormalOrderRepository;
    private final CustomerSubscriptionService customerSubscriptionService;
    private final InvoiceService invoiceService;
    private final PaymentChannelRouter paymentChannelRouter;

    public void reconcileSubscription(AbnormalOrder retryCandidate) {
        String subscriptionNo = retryCandidate.getOrderNo();
        if (subscriptionNo == null || subscriptionNo.isBlank()) {
            scheduleRetry(retryCandidate, "subscription tracking id missing");
            return;
        }

        Optional<CustomerSubscription> localSubscription = customerSubscriptionService.findBySubscriptionNo(subscriptionNo);
        if (localSubscription.isPresent()) {
            CustomerSubscription customerSubscription = localSubscription.get();
            String providerSubscriptionId = resolveProviderSubscriptionId(customerSubscription, retryCandidate);
            if (providerSubscriptionId == null || providerSubscriptionId.isBlank()) {
                scheduleRetry(retryCandidate, "provider subscription id missing");
                return;
            }

            if (!claimReconciling(providerSubscriptionId, retryCandidate)) {
                return;
            }

            PaymentChannel paymentChannel = paymentChannelRouter.get(customerSubscription.getProvider().toString());
            ProviderSubscription providerSubscription =
                    paymentChannel.retrieveSubscription(providerSubscriptionId);
            checkSubscription(providerSubscription, retryCandidate, customerSubscription);
            return;
        }

        if (retryCandidate.getRetryCount() >= MAX_RETRY_COUNT
                || retryCandidate.getStatus() == AbnormalOrderStatus.EXHAUSTED
                || retryCandidate.getStatus() == AbnormalOrderStatus.RETRY_EXHAUSTED) {
            markManualReview(retryCandidate, "subscription still missing after retries");
            return;
        }

        scheduleRetry(retryCandidate, "subscription_missing: " + subscriptionNo);
    }

    public void reconcileInvoice(AbnormalOrder retryCandidate) {
        PaymentChannel paymentChannel = paymentChannelRouter.get(retryCandidate.getProvider());
        String trackedBusinessOrderNo = resolveInvoiceBusinessOrderNo(retryCandidate);
        Optional<CustomerSubscription> localSubscription =
                findTrackedSubscription(trackedBusinessOrderNo);

        String invoiceTrackingId = resolveInvoiceTrackingId(retryCandidate);
        if (invoiceTrackingId == null || invoiceTrackingId.isBlank()) {
            scheduleRetry(retryCandidate, "invoice id missing");
            return;
        }

        if (!claimReconciling(invoiceTrackingId, retryCandidate)) {
            return;
        }
        ProviderInvoice providerInvoice = paymentChannel.retrieveInvoice(invoiceTrackingId);

        checkInvoice(providerInvoice, retryCandidate, trackedBusinessOrderNo, localSubscription);
    }

    private boolean claimReconciling(String providerTrackingId, AbnormalOrder retryCandidate) {
        LocalDateTime now = LocalDateTime.now();
        int updated = abnormalOrderRepository.updateStatusToReconcilingBySessionId(
                providerTrackingId,
                AbnormalOrderStatus.RECONCILING,
                now,
                now.minus(RECONCILING_LEASE_TIMEOUT)
        );
        return updated != 0;
    }

    private void checkSubscription(
            ProviderSubscription providerSubscription,
            AbnormalOrder retryCandidate,
            CustomerSubscription local
    ) {
        if (providerSubscription == null) {
            scheduleRetry(retryCandidate, "provider subscription retrieve returned null");
            return;
        }

        String expectedSubscriptionId = resolveProviderSubscriptionId(local, retryCandidate);
        String actualSubscriptionId = providerSubscription.getId();
        if (actualSubscriptionId == null || actualSubscriptionId.isBlank()) {
            scheduleRetry(retryCandidate, "provider subscription id missing in retrieve result");
            return;
        }
        if (!actualSubscriptionId.equals(expectedSubscriptionId)) {
            markManualReview(
                    retryCandidate,
                    "provider subscription id mismatch: expected=" + expectedSubscriptionId + ", actual=" + actualSubscriptionId
            );
            return;
        }

        String objectType = providerSubscription.getObject();
        if (objectType == null || !"subscription".equals(objectType)) {
            markManualReview(
                    retryCandidate,
                    "provider subscription object mismatch: expected=subscription, actual=" + objectType
            );
            return;
        }

        if (hasText(local.getProviderCustomerId())
                && hasText(providerSubscription.getCustomer())
                && !local.getProviderCustomerId().equals(providerSubscription.getCustomer())) {
            markManualReview(
                    retryCandidate,
                    "provider customer mismatch on subscription retrieve: local=" + local.getProviderCustomerId()
                            + ", actual=" + providerSubscription.getCustomer()
            );
            return;
        }

        if (providerSubscription.getMetadata() != null) {
            String providerSubscriptionNo = providerSubscription.getMetadata().get("subscriptionNo");
            if (hasText(providerSubscriptionNo) && !providerSubscriptionNo.equals(local.getSubscriptionNo())) {
                markManualReview(
                        retryCandidate,
                        "subscriptionNo mismatch on provider subscription metadata: local=" + local.getSubscriptionNo()
                                + ", actual=" + providerSubscriptionNo
                );
                return;
            }
        }

        SubscriptionStatus mappedStatus = mapProviderStatus(providerSubscription.getStatus());
        if (mappedStatus == null) {
            scheduleRetry(
                    retryCandidate,
                    "unsupported provider subscription status: " + providerSubscription.getStatus()
            );
            return;
        }

        if (requiresBillingPeriod(mappedStatus)
                && (providerSubscription.currentPeriodStartUtc() == null
                || providerSubscription.currentPeriodEndUtc() == null)) {
            scheduleRetry(
                    retryCandidate,
                    "subscription period missing for status " + providerSubscription.getStatus()
            );
            return;
        }

        backfillSubscriptionFromProvider(local, providerSubscription, mappedStatus);
        markFixed(retryCandidate, "subscription_verified_by_provider:" + providerSubscription.getStatus());
    }

    private void checkInvoice(
            ProviderInvoice providerInvoice,
            AbnormalOrder retryCandidate,
            String trackedBusinessOrderNo,
            Optional<CustomerSubscription> localSubscription
    ) {
        if (providerInvoice == null) {
            scheduleRetry(retryCandidate, "provider invoice retrieve returned null");
            return;
        }

        String expectedInvoiceId = resolveInvoiceTrackingId(retryCandidate);
        String invoiceId = providerInvoice.getId();
        if (invoiceId == null || invoiceId.isBlank()) {
            scheduleRetry(retryCandidate, "provider invoice id missing in retrieve result");
            return;
        }
        if (!invoiceId.equals(expectedInvoiceId)) {
            markManualReview(
                    retryCandidate,
                    "provider invoice id mismatch: expected=" + expectedInvoiceId + ", actual=" + invoiceId
            );
            return;
        }

        String objectType = providerInvoice.getObject();
        if (objectType == null || !"invoice".equals(objectType)) {
            markManualReview(
                    retryCandidate,
                    "provider invoice object mismatch: expected=invoice, actual=" + objectType
            );
            return;
        }

        if (!isSupportedInvoiceStatus(providerInvoice.getStatus())) {
            scheduleRetry(
                    retryCandidate,
                    "unsupported provider invoice status: " + providerInvoice.getStatus()
            );
            return;
        }

        if (!hasText(providerInvoice.getSubscription()) && !hasText(providerInvoice.getCustomer())) {
            scheduleRetry(
                    retryCandidate,
                    "provider invoice identity missing: subscription/customer both blank"
            );
            return;
        }

        String providerBusinessOrderNo = resolveInvoiceBusinessOrderNo(providerInvoice);
        Optional<CustomerSubscription> customerSubscription =
                resolveSubscriptionForInvoice(localSubscription, providerInvoice);
        String resolvedBusinessOrderNo = resolveInvoiceBusinessOrderNo(
                trackedBusinessOrderNo,
                providerBusinessOrderNo,
                customerSubscription
        );
        if (customerSubscription.isPresent()) {
            CustomerSubscription local = customerSubscription.get();
            if (hasText(trackedBusinessOrderNo) && !local.getSubscriptionNo().equals(trackedBusinessOrderNo)) {
                markManualReview(
                        retryCandidate,
                        "subscriptionNo mismatch on invoice reconcile: tracking=" + trackedBusinessOrderNo
                                + ", resolved=" + local.getSubscriptionNo()
                );
                return;
            }
            if (hasText(providerBusinessOrderNo) && !local.getSubscriptionNo().equals(providerBusinessOrderNo)) {
                markManualReview(
                        retryCandidate,
                        "subscriptionNo mismatch on provider invoice metadata: resolved=" + local.getSubscriptionNo()
                                + ", actual=" + providerBusinessOrderNo
                );
                return;
            }
            if (hasText(local.getProviderSubscriptionId())
                    && hasText(providerInvoice.getSubscription())
                    && !local.getProviderSubscriptionId().equals(providerInvoice.getSubscription())) {
                markManualReview(
                        retryCandidate,
                        "provider subscription mismatch on invoice retrieve: local=" + local.getProviderSubscriptionId()
                                + ", actual=" + providerInvoice.getSubscription()
                );
                return;
            }
            if (hasText(local.getProviderCustomerId())
                    && hasText(providerInvoice.getCustomer())
                    && !local.getProviderCustomerId().equals(providerInvoice.getCustomer())) {
                markManualReview(
                        retryCandidate,
                        "provider customer mismatch on invoice retrieve: local=" + local.getProviderCustomerId()
                                + ", actual=" + providerInvoice.getCustomer()
                );
                return;
            }
            backfillSubscriptionIdentityFromInvoice(local, providerInvoice);
            resolvedBusinessOrderNo = local.getSubscriptionNo();
        }

        if (!hasText(resolvedBusinessOrderNo)) {
            scheduleRetry(
                    retryCandidate,
                    "invoice business tracking orderNo missing after provider retrieve"
            );
            return;
        }

        invoiceService.upsertInvoiceByRetrieve(
                retryCandidate.getProvider(),
                providerInvoice,
                resolvedBusinessOrderNo
        );
        markFixed(retryCandidate, "invoice_verified_by_provider:" + providerInvoice.getStatus());

    }

    private String resolveProviderSubscriptionId(CustomerSubscription local, AbnormalOrder abnormalOrder) {
        if (local.getProviderSubscriptionId() != null && !local.getProviderSubscriptionId().isBlank()) {
            return local.getProviderSubscriptionId();
        }
        return abnormalOrder.getSessionId();
    }

    private String resolveInvoiceTrackingId(AbnormalOrder abnormalOrder) {
        if (abnormalOrder == null) {
            return null;
        }
        return abnormalOrder.getSessionId();
    }

    private String resolveInvoiceBusinessOrderNo(AbnormalOrder abnormalOrder) {
        if (abnormalOrder == null) {
            return null;
        }
        return abnormalOrder.getOrderNo();
    }

    private String resolveInvoiceBusinessOrderNo(ProviderInvoice providerInvoice) {
        if (providerInvoice == null || providerInvoice.getMetadata() == null) {
            return null;
        }
        return normalizeText(providerInvoice.getMetadata().get("subscriptionNo"));
    }

    private String resolveInvoiceBusinessOrderNo(
            String trackedBusinessOrderNo,
            String providerBusinessOrderNo,
            Optional<CustomerSubscription> customerSubscription
    ) {
        if (hasText(trackedBusinessOrderNo)) {
            return trackedBusinessOrderNo;
        }
        if (hasText(providerBusinessOrderNo)) {
            return providerBusinessOrderNo;
        }
        if (customerSubscription.isPresent()) {
            return normalizeText(customerSubscription.get().getSubscriptionNo());
        }
        return null;
    }

    private Optional<CustomerSubscription> findTrackedSubscription(String trackedBusinessOrderNo) {
        if (!hasText(trackedBusinessOrderNo)) {
            return Optional.empty();
        }
        return customerSubscriptionService.findBySubscriptionNo(trackedBusinessOrderNo);
    }

    private Optional<CustomerSubscription> resolveSubscriptionForInvoice(
            Optional<CustomerSubscription> localSubscription,
            ProviderInvoice providerInvoice
    ) {
        Optional<CustomerSubscription> local = localSubscription;
        if (local.isPresent()) {
            return local;
        }
        if (providerInvoice.getSubscription() != null && !providerInvoice.getSubscription().isBlank()) {
            local = customerSubscriptionService.findByProviderSubscriptionId(providerInvoice.getSubscription());
            if (local.isPresent()) {
                return local;
            }
        }
        if (providerInvoice.getCustomer() != null && !providerInvoice.getCustomer().isBlank()) {
            return customerSubscriptionService.findByProviderCustomerId(providerInvoice.getCustomer());
        }
        return Optional.empty();
    }

    private void backfillSubscriptionFromProvider(
            CustomerSubscription local,
            ProviderSubscription providerSubscription,
            SubscriptionStatus mappedStatus
    ) {
        boolean changed = false;

        if (providerSubscription.getId() != null
                && !providerSubscription.getId().isBlank()
                && !providerSubscription.getId().equals(local.getProviderSubscriptionId())) {
            local.setProviderSubscriptionId(providerSubscription.getId());
            changed = true;
        }

        if (providerSubscription.getCustomer() != null
                && !providerSubscription.getCustomer().isBlank()
                && !providerSubscription.getCustomer().equals(local.getProviderCustomerId())) {
            local.setProviderCustomerId(providerSubscription.getCustomer());
            changed = true;
        }

        if (providerSubscription.currentPeriodStartUtc() != null
                && !providerSubscription.currentPeriodStartUtc().equals(local.getCurrentPeriodStart())) {
            local.setCurrentPeriodStart(providerSubscription.currentPeriodStartUtc());
            changed = true;
        }

        if (providerSubscription.currentPeriodEndUtc() != null
                && !providerSubscription.currentPeriodEndUtc().equals(local.getCurrentPeriodEnd())) {
            local.setCurrentPeriodEnd(providerSubscription.currentPeriodEndUtc());
            changed = true;
        }

        if (providerSubscription.getCancelAtPeriodEnd() != null
                && !providerSubscription.getCancelAtPeriodEnd().equals(local.getCancelAtPeriodEnd())) {
            local.setCancelAtPeriodEnd(providerSubscription.getCancelAtPeriodEnd());
            changed = true;
        }

        if (providerSubscription.canceledAtUtc() != null
                && !providerSubscription.canceledAtUtc().equals(local.getCanceledAt())) {
            local.setCanceledAt(providerSubscription.canceledAtUtc());
            changed = true;
        }

        if (mappedStatus != local.getStatus()) {
            local.setStatus(mappedStatus);
            changed = true;
        }

        if (changed) {
            local.setUpdatedAt(LocalDateTime.now());
            customerSubscriptionService.save(local);
        }
    }

    private void backfillSubscriptionIdentityFromInvoice(
            CustomerSubscription local,
            ProviderInvoice providerInvoice
    ) {
        boolean changed = false;
        if (providerInvoice.getSubscription() != null
                && !providerInvoice.getSubscription().isBlank()
                && !providerInvoice.getSubscription().equals(local.getProviderSubscriptionId())) {
            local.setProviderSubscriptionId(providerInvoice.getSubscription());
            changed = true;
        }
        if (providerInvoice.getCustomer() != null
                && !providerInvoice.getCustomer().isBlank()
                && !providerInvoice.getCustomer().equals(local.getProviderCustomerId())) {
            local.setProviderCustomerId(providerInvoice.getCustomer());
            changed = true;
        }
        if (changed) {
            local.setUpdatedAt(LocalDateTime.now());
            customerSubscriptionService.save(local);
        }
    }

    private SubscriptionStatus mapProviderStatus(String providerStatus) {
        if (providerStatus == null || providerStatus.isBlank()) {
            return null;
        }
        return switch (providerStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "past_due", "unpaid", "incomplete", "incomplete_expired" -> SubscriptionStatus.PAST_DUE;
            case "paused" -> SubscriptionStatus.PAUSED;
            case "canceled" -> SubscriptionStatus.CANCELED;
            default -> null;
        };
    }

    private boolean requiresBillingPeriod(SubscriptionStatus subscriptionStatus) {
        return subscriptionStatus == SubscriptionStatus.ACTIVE
                || subscriptionStatus == SubscriptionStatus.TRIALING
                || subscriptionStatus == SubscriptionStatus.PAST_DUE
                || subscriptionStatus == SubscriptionStatus.PAUSED;
    }

    private boolean isSupportedInvoiceStatus(String providerStatus) {
        if (providerStatus == null || providerStatus.isBlank()) {
            return false;
        }
        return "paid".equals(providerStatus)
                || "open".equals(providerStatus)
                || "draft".equals(providerStatus)
                || "void".equals(providerStatus)
                || "uncollectible".equals(providerStatus);
    }

    public void markManualReview(AbnormalOrder retryCandidate, String message) {
        LocalDateTime now = LocalDateTime.now();
        retryCandidate.setStatus(AbnormalOrderStatus.MANUAL_REVIEW);
        retryCandidate.setUpdatedAt(now);
        retryCandidate.setLastRetryAt(now);
        retryCandidate.setHandledAt(now);
        retryCandidate.setErrorMessage(message);
        abnormalOrderRepository.save(retryCandidate);
    }

    private void scheduleRetry(AbnormalOrder retryCandidate, String message) {
        int nextRetryCount = retryCandidate.getRetryCount() + 1;
        LocalDateTime now = LocalDateTime.now();
        retryCandidate.setRetryCount(nextRetryCount);
        retryCandidate.setLastRetryAt(now);
        retryCandidate.setUpdatedAt(now);
        retryCandidate.setErrorMessage(message);
        retryCandidate.setNextRetryAt(now.plus(processingStale.multipliedBy(nextRetryCount)));
        if (nextRetryCount >= MAX_RETRY_COUNT) {
            retryCandidate.setStatus(AbnormalOrderStatus.EXHAUSTED);
        } else {
            retryCandidate.setStatus(AbnormalOrderStatus.PENDING);
        }
        abnormalOrderRepository.save(retryCandidate);
    }

    private void markFixed(AbnormalOrder retryCandidate, String message) {
        LocalDateTime now = LocalDateTime.now();
        retryCandidate.setStatus(AbnormalOrderStatus.FIXED);
        retryCandidate.setUpdatedAt(now);
        retryCandidate.setLastRetryAt(now);
        retryCandidate.setHandledAt(now);
        retryCandidate.setErrorMessage(message);
        abnormalOrderRepository.save(retryCandidate);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeText(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value;
    }
}
