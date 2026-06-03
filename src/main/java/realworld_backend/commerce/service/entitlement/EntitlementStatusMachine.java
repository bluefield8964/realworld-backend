package realworld_backend.commerce.service.entitlement;

import realworld_backend.common.time.UtcTimeMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.model.PaymentStatus;
import realworld_backend.commerce.model.invoice.Invoice;
import realworld_backend.commerce.model.entitlement.enums.EntitlementStatus;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.repository.InvoiceRepository;

import java.time.LocalDateTime;
import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class EntitlementStatusMachine {
    private final InvoiceRepository invoiceRepository;

    public EntitlementStatusDecision evaluateSubscription(CustomerSubscription subscription, LocalDateTime now) {
        if (subscription == null || subscription.getStatus() == null) {
            return new EntitlementStatusDecision(
                    EntitlementStatus.REVOKED,
                    false,
                    false,
                    "missing subscription state"
            );
        }

        LocalDateTime effectiveNow = now == null ? UtcTimeMapper.nowUtc() : now;
        SubscriptionStatus status = subscription.getStatus();
        LocalDateTime currentPeriodEnd = subscription.getCurrentPeriodEnd();
        boolean notExpired = currentPeriodEnd == null || effectiveNow.isBefore(currentPeriodEnd);

        if (status == SubscriptionStatus.TRIALING && notExpired) {
            return new EntitlementStatusDecision(
                    EntitlementStatus.ACTIVE,
                    true,
                    false,
                    "subscription trialing"
            );
        }

        if (status == SubscriptionStatus.ACTIVE && notExpired && hasGrantableInvoiceForCurrentCycle(subscription)) {
            return new EntitlementStatusDecision(
                    EntitlementStatus.ACTIVE,
                    true,
                    false,
                    subscription.getCancelAtPeriodEnd() == Boolean.TRUE
                            ? "subscription active with verified invoice and set to cancel at period end"
                            : "subscription active with verified invoice"
            );
        }

        if (status == SubscriptionStatus.ACTIVE && notExpired) {
            return new EntitlementStatusDecision(
                    EntitlementStatus.REVOKED,
                    false,
                    false,
                    "subscription active without grantable invoice"
            );
        }

        if (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING) {
            return new EntitlementStatusDecision(
                    EntitlementStatus.EXPIRED,
                    false,
                    true,
                    "subscription period expired"
            );
        }

        if (status == SubscriptionStatus.CANCELED && !notExpired) {
            return new EntitlementStatusDecision(
                    EntitlementStatus.EXPIRED,
                    false,
                    true,
                    "subscription canceled and expired"
            );
        }

        if (status == SubscriptionStatus.CANCELED && notExpired && hasGrantableInvoiceForCurrentCycle(subscription)) {
            return new EntitlementStatusDecision(
                    EntitlementStatus.ACTIVE,
                    true,
                    false,
                    "subscription canceled but still within paid period"
            );
        }

        if (status == SubscriptionStatus.CANCELED) {
            return new EntitlementStatusDecision(
                    EntitlementStatus.REVOKED,
                    false,
                    true,
                    "subscription canceled without current-cycle grantable invoice"
            );
        }

        if (EnumSet.of(
                SubscriptionStatus.CHECKOUT_EXPIRED,
                SubscriptionStatus.CHECKOUT_FAIL,
                SubscriptionStatus.INITIAL_FAIL,
                SubscriptionStatus.CREATED,
                SubscriptionStatus.PENDING,
                SubscriptionStatus.PAYING,
                SubscriptionStatus.PAST_DUE,
                SubscriptionStatus.PAUSED
        ).contains(status)) {
            return new EntitlementStatusDecision(
                    EntitlementStatus.REVOKED,
                    false,
                    status == SubscriptionStatus.CHECKOUT_EXPIRED || status == SubscriptionStatus.INITIAL_FAIL,
                    "subscription state does not grant entitlement"
            );
        }

        return new EntitlementStatusDecision(
                EntitlementStatus.REVOKED,
                false,
                false,
                "subscription state not mapped"
        );
    }

    private boolean hasGrantableInvoiceForCurrentCycle(CustomerSubscription subscription) {
        if (subscription == null) {
            return false;
        }
        String subscriptionNo = subscription.getSubscriptionNo();
        if (subscriptionNo == null || subscriptionNo.isBlank()) {
            return false;
        }
        LocalDateTime currentPeriodStart = subscription.getCurrentPeriodStart();
        LocalDateTime currentPeriodEnd = subscription.getCurrentPeriodEnd();

        if (currentPeriodStart != null && currentPeriodEnd != null) {
            return invoiceRepository
                    .findTopBySubscriptionNoAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByProviderCreatedAtDescUpdatedAtDesc(
                            subscriptionNo,
                            currentPeriodStart,
                            currentPeriodEnd
                    )
                    .map(this::isGrantableInvoice)
                    .orElse(false);
        }

        return invoiceRepository.findTopBySubscriptionNoOrderByProviderCreatedAtDescUpdatedAtDesc(subscriptionNo)
                .map(this::isGrantableInvoice)
                .orElse(false);
    }

    private boolean isGrantableInvoice(Invoice invoice) {
        if (invoice == null) {
            return false;
        }
        if (Boolean.TRUE.equals(invoice.getPaid())) {
            return true;
        }
        if (invoice.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return true;
        }
        Long amountDue = invoice.getAmountDue();
        return amountDue != null && amountDue == 0L;
    }
}

