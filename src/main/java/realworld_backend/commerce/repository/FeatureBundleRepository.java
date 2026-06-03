package realworld_backend.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.commerce.model.subscription.FeatureBundle;

import java.util.Optional;

@Repository
public interface FeatureBundleRepository extends JpaRepository<FeatureBundle, Long> {
    Optional<FeatureBundle> findByBundleCode(String bundleCode);
}
