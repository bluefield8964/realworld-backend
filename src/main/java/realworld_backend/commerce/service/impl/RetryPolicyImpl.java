package realworld_backend.commerce.service.impl;

import org.springframework.stereotype.Service;
import realworld_backend.commerce.service.core.RetryPolicy;

import java.time.Duration;
import java.time.Instant;

@Service
public class RetryPolicyImpl implements RetryPolicy {
    // Retry cap before moving event to DEAD.
    private final int MAX_ATTEMPTS = 5;
    // Base lease window for PROCESSING takeover checks.
    private final Duration PROCESSING_STALE_SEC = Duration.ofSeconds(30);
    private final Duration PROCESSING_STALE_MIN = Duration.ofMinutes(3);

    private final Duration RECONCILE_STALE_MIN = Duration.ofSeconds(30);


    @Override
    public Instant mainStreamNextRetryAt(int attempts, Instant now) {
        if (attempts <= 2) {
            return now.plus(PROCESSING_STALE_SEC);
        } else {
            return now.plus(PROCESSING_STALE_MIN.multipliedBy(attempts));
        }

    }

    @Override
    public Instant reconcileNextRetryAt(int attempts, Instant now) {

        return now.plus(RECONCILE_STALE_MIN.multipliedBy(attempts));
    }

    @Override
    public boolean exhausted(int attempts) {
        return attempts >= MAX_ATTEMPTS;
    }
}

