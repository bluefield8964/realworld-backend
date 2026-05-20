package realworld_backend.commerce.service.core;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Normalizes provider timestamps into internal time types.
 * Supports both epoch-seconds and epoch-millis inputs.
 */
public final class ProviderTimeMapper {

    private static final long MILLIS_THRESHOLD = 9_999_999_999L;
    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private ProviderTimeMapper() {
    }

    public static Instant toInstant(Long providerEpoch) {
        if (providerEpoch == null) {
            return null;
        }
        if (Math.abs(providerEpoch) > MILLIS_THRESHOLD) {
            return Instant.ofEpochMilli(providerEpoch);
        }
        return Instant.ofEpochSecond(providerEpoch);
    }

    public static LocalDateTime toUtcLocalDateTime(Long providerEpoch) {
        Instant instant = toInstant(providerEpoch);
        return toUtcLocalDateTime(instant);
    }

    public static LocalDateTime toUtcLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, UTC);
    }

    public static Instant toInstant(LocalDateTime localDateTimeUtc) {
        if (localDateTimeUtc == null) {
            return null;
        }
        return localDateTimeUtc.toInstant(UTC);
    }
}
