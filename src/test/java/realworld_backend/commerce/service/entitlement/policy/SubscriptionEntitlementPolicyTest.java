package realworld_backend.commerce.service.entitlement.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import realworld_backend.auth.domain.model.UserAuthProfile;
import realworld_backend.commerce.model.PaymentStatus;
import realworld_backend.commerce.model.invoice.Invoice;
import realworld_backend.commerce.model.entitlement.enums.EntitlementResourceType;
import realworld_backend.commerce.model.entitlement.enums.EntitlementStatus;
import realworld_backend.commerce.model.subscription.FeatureBundle;
import realworld_backend.commerce.model.subscription.CustomerSubscription;
import realworld_backend.commerce.model.subscription.SubscriptionPlan;
import realworld_backend.commerce.model.subscription.enums.FeatureBundleStatus;
import realworld_backend.commerce.model.subscription.enums.FeatureCode;
import realworld_backend.commerce.model.subscription.enums.SubscriptionStatus;
import realworld_backend.commerce.repository.InvoiceRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionEntitlementPolicyTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Test
    void activeSubscriptionShouldProjectActiveEntitlement() {
        SubscriptionEntitlementPolicy policy =
                new SubscriptionEntitlementPolicy(new realworld_backend.commerce.service.entitlement.EntitlementStatusMachine(invoiceRepository));
        FeatureBundle bundle = FeatureBundle.builder()
                .bundleCode("CREATOR_PRO_BUNDLE")
                .name("Creator Pro Bundle")
                .status(FeatureBundleStatus.ACTIVE)
                .features(Set.of(FeatureCode.CREATOR_POST_ACCESS, FeatureCode.CREATOR_MEDIA_ACCESS))
                .build();
        CustomerSubscription subscription = CustomerSubscription.builder()
                .subscriptionNo("sub_no_1")
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(7))
                .user(UserAuthProfile.builder().id(1L).build())
                .plan(SubscriptionPlan.builder().planCode("PRO").featureBundle(bundle).build())
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

        var projection = policy.projectSubscription(subscription, LocalDateTime.now());

        assertTrue(projection.isPresent());
        assertEquals(EntitlementStatus.ACTIVE, projection.get().status());
        assertEquals(EntitlementResourceType.BUNDLE, projection.get().resourceType());
        assertEquals("CREATOR_PRO_BUNDLE", projection.get().resourceId());
    }

    @Test
    void checkoutExpiredSubscriptionShouldProjectRevokedEntitlement() {
        SubscriptionEntitlementPolicy policy =
                new SubscriptionEntitlementPolicy(new realworld_backend.commerce.service.entitlement.EntitlementStatusMachine(invoiceRepository));
        FeatureBundle bundle = FeatureBundle.builder()
                .bundleCode("CREATOR_PRO_BUNDLE")
                .name("Creator Pro Bundle")
                .status(FeatureBundleStatus.ACTIVE)
                .features(Set.of(FeatureCode.CREATOR_POST_ACCESS))
                .build();
        CustomerSubscription subscription = CustomerSubscription.builder()
                .subscriptionNo("sub_no_2")
                .status(SubscriptionStatus.CHECKOUT_EXPIRED)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(7))
                .user(UserAuthProfile.builder().id(2L).build())
                .plan(SubscriptionPlan.builder().planCode("PRO").featureBundle(bundle).build())
                .build();

        var projection = policy.projectSubscription(subscription, LocalDateTime.now());

        assertTrue(projection.isPresent());
        assertEquals(EntitlementStatus.REVOKED, projection.get().status());
    }

    @Test
    void activeSubscriptionMarkedCancelAtPeriodEndShouldStillProjectActiveEntitlement() {
        SubscriptionEntitlementPolicy policy =
                new SubscriptionEntitlementPolicy(new realworld_backend.commerce.service.entitlement.EntitlementStatusMachine(invoiceRepository));
        FeatureBundle bundle = FeatureBundle.builder()
                .bundleCode("CREATOR_PRO_BUNDLE")
                .name("Creator Pro Bundle")
                .status(FeatureBundleStatus.ACTIVE)
                .features(Set.of(FeatureCode.CREATOR_POST_ACCESS))
                .build();
        CustomerSubscription subscription = CustomerSubscription.builder()
                .subscriptionNo("sub_no_3")
                .status(SubscriptionStatus.ACTIVE)
                .cancelAtPeriodEnd(true)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(7))
                .user(UserAuthProfile.builder().id(3L).build())
                .plan(SubscriptionPlan.builder().planCode("PRO").featureBundle(bundle).build())
                .build();
        Invoice invoice = Invoice.builder()
                .subscriptionNo("sub_no_3")
                .paymentStatus(PaymentStatus.SUCCESS)
                .paid(true)
                .build();
        when(invoiceRepository.findTopBySubscriptionNoAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByProviderCreatedAtDescUpdatedAtDesc(
                org.mockito.ArgumentMatchers.eq("sub_no_3"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        ))
                .thenReturn(Optional.of(invoice));

        var projection = policy.projectSubscription(subscription, LocalDateTime.now());

        assertTrue(projection.isPresent());
        assertEquals(EntitlementStatus.ACTIVE, projection.get().status());
    }

    @Test
    void canceledSubscriptionBeforePeriodEndShouldStillProjectActiveEntitlement() {
        SubscriptionEntitlementPolicy policy =
                new SubscriptionEntitlementPolicy(new realworld_backend.commerce.service.entitlement.EntitlementStatusMachine(invoiceRepository));
        FeatureBundle bundle = FeatureBundle.builder()
                .bundleCode("CREATOR_PRO_BUNDLE")
                .name("Creator Pro Bundle")
                .status(FeatureBundleStatus.ACTIVE)
                .features(Set.of(FeatureCode.CREATOR_POST_ACCESS))
                .build();
        CustomerSubscription subscription = CustomerSubscription.builder()
                .subscriptionNo("sub_no_4")
                .status(SubscriptionStatus.CANCELED)
                .currentPeriodStart(LocalDateTime.now().minusDays(1))
                .currentPeriodEnd(LocalDateTime.now().plusDays(7))
                .user(UserAuthProfile.builder().id(4L).build())
                .plan(SubscriptionPlan.builder().planCode("PRO").featureBundle(bundle).build())
                .build();
        Invoice invoice = Invoice.builder()
                .subscriptionNo("sub_no_4")
                .paymentStatus(PaymentStatus.SUCCESS)
                .paid(true)
                .periodStart(LocalDateTime.now().minusDays(1))
                .periodEnd(LocalDateTime.now().plusDays(7))
                .build();
        when(invoiceRepository.findTopBySubscriptionNoAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByProviderCreatedAtDescUpdatedAtDesc(
                org.mockito.ArgumentMatchers.eq("sub_no_4"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        ))
                .thenReturn(Optional.of(invoice));

        var projection = policy.projectSubscription(subscription, LocalDateTime.now());

        assertTrue(projection.isPresent());
        assertEquals(EntitlementStatus.ACTIVE, projection.get().status());
    }
}
