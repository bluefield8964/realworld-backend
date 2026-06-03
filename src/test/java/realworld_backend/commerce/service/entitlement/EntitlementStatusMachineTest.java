package realworld_backend.commerce.service.entitlement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import realworld_backend.commerce.model.PaymentStatus;
import realworld_backend.commerce.model.invoice.Invoice;
import realworld_backend.commerce.model.entitlement.enums.EntitlementStatus;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.repository.InvoiceRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementStatusMachineTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Test
    void activeSubscriptionShouldGrantAliveEntitlementWhenInvoiceIsPaid() {
        EntitlementStatusMachine machine = new EntitlementStatusMachine(invoiceRepository);
        CustomerSubscription subscription = CustomerSubscription.builder()
                .subscriptionNo("sub_no_1")
                .status(SubscriptionStatus.ACTIVE)
                .cancelAtPeriodEnd(false)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(3))
                .build();
        Invoice invoice = Invoice.builder()
                .subscriptionNo("sub_no_1")
                .paymentStatus(PaymentStatus.SUCCESS)
                .paid(true)
                .build();
        when(invoiceRepository.findTopBySubscriptionNoAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByProviderCreatedAtDescUpdatedAtDesc(
                org.mockito.ArgumentMatchers.eq("sub_no_1"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        ))
                .thenReturn(Optional.of(invoice));

        EntitlementStatusDecision decision = machine.evaluateSubscription(subscription, LocalDateTime.now());

        assertEquals(EntitlementStatus.ACTIVE, decision.status());
        assertTrue(decision.alive());
        assertFalse(decision.terminal());
    }

    @Test
    void activeSubscriptionCancelAtPeriodEndShouldStillRemainAliveBeforeExpiry() {
        EntitlementStatusMachine machine = new EntitlementStatusMachine(invoiceRepository);
        CustomerSubscription subscription = CustomerSubscription.builder()
                .subscriptionNo("sub_no_2")
                .status(SubscriptionStatus.ACTIVE)
                .cancelAtPeriodEnd(true)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(3))
                .build();
        Invoice invoice = Invoice.builder()
                .subscriptionNo("sub_no_2")
                .paymentStatus(PaymentStatus.SUCCESS)
                .paid(true)
                .build();
        when(invoiceRepository.findTopBySubscriptionNoAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByProviderCreatedAtDescUpdatedAtDesc(
                org.mockito.ArgumentMatchers.eq("sub_no_2"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        ))
                .thenReturn(Optional.of(invoice));

        EntitlementStatusDecision decision = machine.evaluateSubscription(subscription, LocalDateTime.now());

        assertEquals(EntitlementStatus.ACTIVE, decision.status());
        assertTrue(decision.alive());
    }

    @Test
    void checkoutExpiredSubscriptionShouldRevokeEntitlement() {
        EntitlementStatusMachine machine = new EntitlementStatusMachine(invoiceRepository);
        CustomerSubscription subscription = CustomerSubscription.builder()
                .status(SubscriptionStatus.CHECKOUT_EXPIRED)
                .currentPeriodEnd(LocalDateTime.now().plusDays(3))
                .build();

        EntitlementStatusDecision decision = machine.evaluateSubscription(subscription, LocalDateTime.now());

        assertEquals(EntitlementStatus.REVOKED, decision.status());
        assertFalse(decision.alive());
        assertTrue(decision.terminal());
    }

    @Test
    void expiredActiveSubscriptionShouldExpireEntitlement() {
        EntitlementStatusMachine machine = new EntitlementStatusMachine(invoiceRepository);
        CustomerSubscription subscription = CustomerSubscription.builder()
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(LocalDateTime.now().minusSeconds(1))
                .build();

        EntitlementStatusDecision decision = machine.evaluateSubscription(subscription, LocalDateTime.now());

        assertEquals(EntitlementStatus.EXPIRED, decision.status());
        assertFalse(decision.alive());
        assertTrue(decision.terminal());
    }

    @Test
    void activeSubscriptionWithoutGrantableInvoiceShouldRevokeEntitlement() {
        EntitlementStatusMachine machine = new EntitlementStatusMachine(invoiceRepository);
        CustomerSubscription subscription = CustomerSubscription.builder()
                .subscriptionNo("sub_no_3")
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(3))
                .build();
        when(invoiceRepository.findTopBySubscriptionNoAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByProviderCreatedAtDescUpdatedAtDesc(
                org.mockito.ArgumentMatchers.eq("sub_no_3"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        ))
                .thenReturn(Optional.empty());

        EntitlementStatusDecision decision = machine.evaluateSubscription(subscription, LocalDateTime.now());

        assertEquals(EntitlementStatus.REVOKED, decision.status());
        assertFalse(decision.alive());
        assertFalse(decision.terminal());
    }

    @Test
    void canceledSubscriptionShouldRemainActiveBeforeCurrentPeriodEndWhenCurrentCycleInvoiceIsGrantable() {
        EntitlementStatusMachine machine = new EntitlementStatusMachine(invoiceRepository);
        CustomerSubscription subscription = CustomerSubscription.builder()
                .subscriptionNo("sub_no_4")
                .status(SubscriptionStatus.CANCELED)
                .currentPeriodStart(LocalDateTime.now().minusDays(2))
                .currentPeriodEnd(LocalDateTime.now().plusDays(2))
                .build();
        Invoice invoice = Invoice.builder()
                .subscriptionNo("sub_no_4")
                .paymentStatus(PaymentStatus.SUCCESS)
                .paid(true)
                .periodStart(LocalDateTime.now().minusDays(2))
                .periodEnd(LocalDateTime.now().plusDays(2))
                .build();
        when(invoiceRepository.findTopBySubscriptionNoAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByProviderCreatedAtDescUpdatedAtDesc(
                org.mockito.ArgumentMatchers.eq("sub_no_4"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        ))
                .thenReturn(Optional.of(invoice));

        EntitlementStatusDecision decision = machine.evaluateSubscription(subscription, LocalDateTime.now());

        assertEquals(EntitlementStatus.ACTIVE, decision.status());
        assertTrue(decision.alive());
        assertFalse(decision.terminal());
    }
}
