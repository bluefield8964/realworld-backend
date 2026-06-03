package realworld_backend.commerce.model.subscription;

import org.junit.jupiter.api.Test;
import realworld_backend.commerce.model.subscription.enums.FeatureBundleStatus;
import realworld_backend.commerce.model.subscription.enums.FeatureCode;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionPlanFeatureBundleModelTest {

    @Test
    void subscriptionPlanShouldReferenceFixedFeatureBundle() {
        FeatureBundle bundle = FeatureBundle.builder()
                .bundleCode("CREATOR_PRO_BUNDLE")
                .name("Creator Pro Bundle")
                .status(FeatureBundleStatus.ACTIVE)
                .features(Set.of(
                        FeatureCode.CREATOR_POST_ACCESS,
                        FeatureCode.CREATOR_MEDIA_ACCESS,
                        FeatureCode.CREATOR_MESSAGE_ACCESS
                ))
                .build();

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .planCode("CREATOR_PRO_MONTHLY")
                .name("Creator Pro Monthly")
                .featureBundle(bundle)
                .build();

        assertEquals("CREATOR_PRO_BUNDLE", plan.getFeatureBundle().getBundleCode());
        assertTrue(plan.getFeatureBundle().getFeatures().contains(FeatureCode.CREATOR_MEDIA_ACCESS));
        assertEquals(3, plan.getFeatureBundle().getFeatures().size());
    }
}
