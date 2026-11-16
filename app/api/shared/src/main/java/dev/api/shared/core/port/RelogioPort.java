package dev.api.shared.core.port;

import java.time.Instant;
import java.time.ZoneId;

public interface RelogioPort {
    Instant agora();

    ZoneId fuso();
}
