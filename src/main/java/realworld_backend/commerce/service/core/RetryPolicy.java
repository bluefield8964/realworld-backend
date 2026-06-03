package realworld_backend.commerce.service.core;

import java.time.Instant;

public interface RetryPolicy {
    Instant reconcileNextRetryAt(int attempts, Instant now);
    boolean exhausted(int attempts);
    Instant mainStreamNextRetryAt(int attempts, Instant now);

}

