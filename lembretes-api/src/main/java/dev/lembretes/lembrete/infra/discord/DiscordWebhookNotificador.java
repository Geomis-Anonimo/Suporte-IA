package dev.lembretes.lembrete.infra.discord;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.lembretes.lembrete.core.application.port.NotificadorPort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Envia o aviso para um canal do Discord via webhook. A URL e segredo: vem de variavel de ambiente. */
public class DiscordWebhookNotificador implements NotificadorPort {
    private static final int LIMITE_DISCORD = 2000;

    private final String webhookUrl;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public DiscordWebhookNotificador(String webhookUrl, ObjectMapper json) {
        this.webhookUrl = webhookUrl;
        this.json = json;
    }

    @Override
    public void enviar(String mensagem) throws NotificacaoException {
        if (webhookUrl == null || webhookUrl.isBlank())
            throw new NotificacaoException("DISCORD_WEBHOOK_URL nao configurada");
        String texto = mensagem.length() > LIMITE_DISCORD ? mensagem.substring(0, LIMITE_DISCORD - 1) + "…" : mensagem;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("content", texto))))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2)
                throw new NotificacaoException("Discord respondeu HTTP " + res.statusCode());
        } catch (JsonProcessingException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new NotificacaoException("Falha ao chamar o Discord: " + e.getClass().getSimpleName(), e);
        } catch (IOException e) {
            throw new NotificacaoException("Falha de rede ao chamar o Discord", e);
        }
    }
}
