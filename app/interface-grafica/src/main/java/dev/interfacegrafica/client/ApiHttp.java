package dev.interfacegrafica.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Base HTTP dos clientes da api (sem JavaFX). Converte erros em {@link ApiException} (texto de {"erro"})
 * ou {@link ApiOfflineException}. Cada modulo da api tem seu cliente, que estende esta classe.
 */
public abstract class ApiHttp {
    private static final Duration TIMEOUT_CONEXAO = Duration.ofSeconds(5);
    private static final Duration TIMEOUT_REQUISICAO = Duration.ofSeconds(10);

    /** URL base sem barra no fim, ex.: http://127.0.0.1:8080 */
    protected final String base;
    private final HttpClient http;
    protected final ObjectMapper mapper;

    protected ApiHttp(String baseUrl) {
        this.base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT_CONEXAO).build();
        this.mapper = criarMapper();
    }

    public static ObjectMapper criarMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }

    // ------------------------------------------------------------- verbos

    protected <T> T get(String caminho, TypeReference<T> tipo) {
        return ler(enviar(requisicao(caminho).GET()), tipo);
    }

    protected <T> T post(String caminho, Object corpo, TypeReference<T> tipo) {
        HttpRequest.BodyPublisher pub = corpo == null ? HttpRequest.BodyPublishers.noBody() : corpo(corpo);
        return ler(enviar(requisicao(caminho).POST(pub)), tipo);
    }

    protected <T> T put(String caminho, Object corpo, TypeReference<T> tipo) {
        return ler(enviar(requisicao(caminho).PUT(corpo(corpo))), tipo);
    }

    protected void delete(String caminho) {
        enviar(requisicao(caminho).DELETE());
    }

    /** Monta "?a=1&b=2" ignorando valores nulos; valores repetidos via varios pares. */
    protected static String query(Object... chaveValor) {
        List<String> partes = new ArrayList<>();
        for (int i = 0; i + 1 < chaveValor.length; i += 2) {
            Object v = chaveValor[i + 1];
            if (v == null) continue;
            if (v instanceof Iterable<?> lista) {
                for (Object item : lista) partes.add(chaveValor[i] + "=" + codificar(item));
            } else {
                partes.add(chaveValor[i] + "=" + codificar(v));
            }
        }
        return partes.isEmpty() ? "" : "?" + String.join("&", partes);
    }

    private static String codificar(Object v) {
        return URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------- internos

    private HttpRequest.Builder requisicao(String caminho) {
        return HttpRequest.newBuilder(URI.create(base + caminho))
                .timeout(TIMEOUT_REQUISICAO)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
    }

    private HttpRequest.BodyPublisher corpo(Object valor) {
        try {
            return HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(valor), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new ApiException(0, "Nao foi possivel montar o JSON: " + e.getOriginalMessage(), e);
        }
    }

    private String enviar(HttpRequest.Builder req) {
        HttpResponse<String> resp;
        try {
            resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (ConnectException | HttpConnectTimeoutException e) {
            throw new ApiOfflineException("API fora do ar", e);
        } catch (HttpTimeoutException e) {
            throw new ApiOfflineException("A API nao respondeu a tempo", e);
        } catch (IOException e) {
            if (e.getCause() instanceof ConnectException) throw new ApiOfflineException("API fora do ar", e);
            throw new ApiException(0, "Falha de comunicacao com a API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "Requisicao interrompida", e);
        }
        if (resp.statusCode() >= 400) throw new ApiException(resp.statusCode(), mensagemDeErro(resp));
        return resp.body();
    }

    private String mensagemDeErro(HttpResponse<String> resp) {
        try {
            String erro = mapper.readTree(resp.body()).path("erro").asText(null);
            if (erro != null && !erro.isBlank()) return erro;
        } catch (IOException | RuntimeException ignorado) {
            // corpo nao e JSON: cai na mensagem generica
        }
        return "HTTP " + resp.statusCode();
    }

    private <T> T ler(String corpo, TypeReference<T> tipo) {
        if (tipo == null || corpo == null || corpo.isEmpty()) return null;
        try {
            return mapper.readValue(corpo, tipo);
        } catch (JsonProcessingException e) {
            throw new ApiException(0, "Resposta inesperada da API: " + e.getOriginalMessage(), e);
        }
    }
}
