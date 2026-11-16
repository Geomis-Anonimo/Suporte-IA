package dev.api.shared.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.api.shared.core.port.NotificadorPort;
import dev.api.shared.core.port.RelogioPort;
import dev.api.shared.infra.discord.DiscordWebhookNotificador;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adaptadores compartilhados por todos os modulos: relogio/fuso e envio ao Discord. */
@Configuration
public class SharedConfig {

    @Bean
    RelogioPort relogio(@Value("${api.fuso}") String fuso) {
        ZoneId zona = ZoneId.of(fuso);
        return new RelogioPort() {
            public Instant agora() { return Instant.now(); }
            public ZoneId fuso() { return zona; }
        };
    }

    @Bean
    NotificadorPort notificador(@Value("${api.discord.webhook-url}") String url, ObjectMapper json) {
        return new DiscordWebhookNotificador(url, json);
    }
}
