package dev.interfacegrafica.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.interfacegrafica.client.dto.LembreteRequest;
import dev.interfacegrafica.client.dto.LembreteResponse;
import dev.interfacegrafica.client.dto.ParticipanteDto;
import dev.interfacegrafica.client.dto.RecorrenciaDto;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LembretesClientTest {

    private static final String LEMBRETE_JSON = """
            {"id":7,"titulo":"Daily","descricao":null,"mensagem":"Bom dia","recorrencia":{"tipo":"SEMANAL","dataHora":null,
            "hora":"09:00:00","diaSemana":"THURSDAY","diaMes":null},"antecedenciaMin":5,"contextoMd":"time/README.md",
            "participantes":[{"nome":"Ana","detalhe":"PO"}],"proximaOcorrencia":"2026-11-19T09:00:00-03:00",
            "proximoDisparo":"2026-11-19T08:55:00-03:00","ativo":true,"campoNovoDaApi":"ignorar"}
            """;

    private HttpServer servidor;
    private LembretesClient client;
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> caminho = new AtomicReference<>();
    private final AtomicReference<String> corpoRecebido = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String resposta = "[]";

    @BeforeEach
    void subir() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            metodo.set(troca.getRequestMethod());
            caminho.set(troca.getRequestURI().toString());
            corpoRecebido.set(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
            if (status == 204) {
                troca.sendResponseHeaders(204, -1);
            } else {
                troca.getResponseHeaders().add("Content-Type", "application/json");
                troca.sendResponseHeaders(status, bytes.length);
                try (OutputStream os = troca.getResponseBody()) {
                    os.write(bytes);
                }
            }
            troca.close();
        });
        servidor.start();
        client = new LembretesClient("http://127.0.0.1:" + servidor.getAddress().getPort() + "/");
    }

    @AfterEach
    void descer() {
        servidor.stop(0);
    }

    @Test
    void listarSemFiltroELeCamposEIgnoraDesconhecidos() {
        resposta = "[" + LEMBRETE_JSON + "]";
        List<LembreteResponse> lista = client.listar(null);

        assertEquals("GET", metodo.get());
        assertEquals("/api/lembretes", caminho.get());
        assertEquals(1, lista.size());
        LembreteResponse l = lista.get(0);
        assertEquals(7L, l.id());
        assertEquals(RecorrenciaDto.Tipo.SEMANAL, l.recorrencia().tipo());
        assertEquals(DayOfWeek.THURSDAY, l.recorrencia().diaSemana());
        assertEquals(LocalTime.of(9, 0), l.recorrencia().hora());
        assertEquals(-3 * 3600, l.proximoDisparo().getOffset().getTotalSeconds());
        assertEquals("Ana", l.participantes().get(0).nome());
        assertTrue(l.ativo());
    }

    @Test
    void listarComFiltro() {
        client.listar(true);
        assertEquals("/api/lembretes?ativo=true", caminho.get());
        client.listar(false);
        assertEquals("/api/lembretes?ativo=false", caminho.get());
    }

    @Test
    void criarEnviaSoOsCamposDoTipoESemIdOuAtivo() throws IOException {
        status = 201;
        resposta = LEMBRETE_JSON;
        var req = new LembreteRequest("Reunião", null, "Texto", RecorrenciaDto.unica(LocalDateTime.of(2026, 11, 20, 15, 0)),
                5, null, List.of(new ParticipanteDto("Mario", "empresa X")));

        LembreteResponse criado = client.criar(req);

        assertEquals("POST", metodo.get());
        assertEquals("/api/lembretes", caminho.get());
        assertEquals(7L, criado.id());
        JsonNode json = new ObjectMapper().readTree(corpoRecebido.get());
        assertFalse(json.has("id"));
        assertFalse(json.has("ativo"));
        assertFalse(json.has("proximoDisparo"));
        assertFalse(json.has("descricao"), "nulos nao sao enviados");
        JsonNode rec = json.get("recorrencia");
        assertEquals("UNICA", rec.get("tipo").asText());
        assertTrue(rec.get("dataHora").asText().startsWith("2026-11-20T15:00"));
        assertFalse(rec.has("hora"));
        assertFalse(rec.has("diaSemana"));
        assertFalse(rec.has("diaMes"));
        assertEquals("Mario", json.get("participantes").get(0).get("nome").asText());
    }

    @Test
    void atualizarSemanalEnviaHoraEDia() throws IOException {
        resposta = LEMBRETE_JSON;
        var req = new LembreteRequest("Daily", null, null, RecorrenciaDto.semanal(DayOfWeek.THURSDAY, LocalTime.of(9, 0)),
                null, null, List.of());

        client.atualizar(7, req);

        assertEquals("PUT", metodo.get());
        assertEquals("/api/lembretes/7", caminho.get());
        JsonNode rec = new ObjectMapper().readTree(corpoRecebido.get()).get("recorrencia");
        assertEquals("THURSDAY", rec.get("diaSemana").asText());
        assertEquals(LocalTime.of(9, 0), LocalTime.parse(rec.get("hora").asText()));
        assertFalse(rec.has("dataHora"));
    }

    @Test
    void excluir204() {
        status = 204;
        client.excluir(7);
        assertEquals("DELETE", metodo.get());
        assertEquals("/api/lembretes/7", caminho.get());
    }

    @Test
    void testarDevolveMensagemEnviada() {
        resposta = "{\"mensagemEnviada\":\"⏰ Daqui 5 min: Daily\"}";
        assertEquals("⏰ Daqui 5 min: Daily", client.testar(7));
        assertEquals("POST", metodo.get());
        assertEquals("/api/lembretes/7/testar", caminho.get());
    }

    @Test
    void erroComCorpoDaApi() {
        for (int codigo : new int[]{400, 404, 502}) {
            status = codigo;
            resposta = "{\"erro\":\"mensagem " + codigo + "\"}";
            ApiException e = assertThrows(ApiException.class, () -> client.obter(1));
            assertEquals(codigo, e.status());
            assertEquals("mensagem " + codigo, e.getMessage());
            assertFalse(e instanceof ApiOfflineException);
        }
    }

    @Test
    void erroSemCorpoJson() {
        status = 500;
        resposta = "<html>falhou</html>";
        ApiException e = assertThrows(ApiException.class, () -> client.listar(null));
        assertEquals(500, e.status());
        assertEquals("HTTP 500", e.getMessage());
    }

    @Test
    void apiForaDoAr() throws IOException {
        int portaLivre;
        try (ServerSocket s = new ServerSocket(0)) {
            portaLivre = s.getLocalPort();
        }
        LembretesClient semApi = new LembretesClient("http://127.0.0.1:" + portaLivre);
        ApiException e = assertThrows(ApiOfflineException.class, () -> semApi.listar(true));
        assertEquals(0, e.status());
    }
}
