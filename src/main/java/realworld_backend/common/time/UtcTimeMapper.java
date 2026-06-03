package realworld_backend.common.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class UtcTimeMapper {

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private UtcTimeMapper() {
    }

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(UTC);
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

