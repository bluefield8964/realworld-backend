package realworld_backend.commerce.service.core;

import java.time.LocalDateTime;

public interface RetryPolicy {
    LocalDateTime reconcileNextRetryAt(int attempts, LocalDateTime now);
    boolean exhausted(int attempts);
        LocalDateTime mainStreamNextRetryAt(int attempts, LocalDateTime now);

}

