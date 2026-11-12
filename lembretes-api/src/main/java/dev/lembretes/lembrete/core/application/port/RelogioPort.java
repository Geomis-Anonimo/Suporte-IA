package dev.lembretes.lembrete.core.application.port;

import java.time.Instant;
import java.time.ZoneId;

public interface RelogioPort {
    Instant agora();

    ZoneId fuso();
}
