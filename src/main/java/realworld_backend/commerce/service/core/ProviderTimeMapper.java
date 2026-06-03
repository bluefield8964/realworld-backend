package realworld_backend.commerce.service.core;

import realworld_backend.common.time.UtcTimeMapper;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Normalizes provider timestamps into internal time types.
 * Supports both epoch-seconds and epoch-millis inputs.
 */
public final class ProviderTimeMapper {

    private static final long MILLIS_THRESHOLD = 9_999_999_999L;

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
        return UtcTimeMapper.toUtcLocalDateTime(instant);
    }

    public static Instant toInstant(LocalDateTime localDateTimeUtc) {
        return UtcTimeMapper.toInstant(localDateTimeUtc);
    }

    public static LocalDateTime nowUtc() {
        return UtcTimeMapper.nowUtc();
    }
}
