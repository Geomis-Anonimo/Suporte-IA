package dev.interfacegrafica.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.interfacegrafica.client.dto.TarefasDtos.Prioridade;
import dev.interfacegrafica.client.dto.TarefasDtos.SquadDto;
import dev.interfacegrafica.client.dto.TarefasDtos.SquadRequest;
import dev.interfacegrafica.client.dto.TarefasDtos.Status;
import dev.interfacegrafica.client.dto.TarefasDtos.TarefaDto;
import dev.interfacegrafica.client.dto.TarefasDtos.TarefaRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TarefasClientTest {
    private static final String TAREFA = """
            {"id":5,"squadId":1,"squadSlug":"guardian","projetoId":4,"projetoSlug":"guardian_whatsapp",
             "projetoNome":"guardian_whatsapp","titulo":"Validar template","descricao":null,"status":"BLOQUEADO",
             "motivoBloqueio":"aguardando Omne","prioridade":"ALTA","tags":["daily"],"link":null,"ordem":1024.0,
             "criadaEm":"2026-11-16T10:00:00-03:00","atualizadaEm":"2026-11-16T10:00:00-03:00","concluidaEm":null}
            """;

    private HttpServer servidor;
    private TarefasClient client;
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> caminho = new AtomicReference<>();
    private final AtomicReference<String> corpo = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String resposta = "[]";

    @BeforeEach
    void subir() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", t -> {
            metodo.set(t.getRequestMethod());
            caminho.set(URLDecoder.decode(t.getRequestURI().toString(), StandardCharsets.UTF_8));
            corpo.set(new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
            if (status == 204) {
                t.sendResponseHeaders(204, -1);
            } else {
                t.sendResponseHeaders(status, bytes.length);
                try (OutputStream os = t.getResponseBody()) {
                    os.write(bytes);
                }
            }
            t.close();
        });
        servidor.start();
        client = new TarefasClient("http://127.0.0.1:" + servidor.getAddress().getPort());
    }

    @AfterEach
    void descer() {
        servidor.stop(0);
    }

    @Test
    void listarSquadsComContagem() {
        resposta = "[{\"id\":1,\"slug\":\"guardian\",\"nome\":\"Guardian\",\"ordem\":1,\"arquivado\":false,"
                + "\"contagem\":{\"A_FAZER\":2,\"FAZENDO\":1,\"BLOQUEADO\":0,\"FEITO\":5}}]";
        List<SquadDto> squads = client.listarSquads(false);
        assertEquals("/api/squads?incluirArquivados=false", caminho.get());
        assertEquals(3, squads.get(0).abertas(), "abertas = tudo menos FEITO");
    }

    @Test
    void listarTarefasComFiltroDeConcluidas() {
        resposta = "[" + TAREFA + "]";
        OffsetDateTime desde = OffsetDateTime.of(2026, 11, 2, 0, 0, 0, 0, ZoneOffset.ofHours(-3));
        List<TarefaDto> lista = client.listarTarefas(1L, desde);
        assertEquals("/api/tarefas?squadId=1&concluidasDesde=2026-11-02T00:00-03:00", caminho.get(),
                "o '+'/'-' do offset precisa ir codificado na URL");
        TarefaDto t = lista.get(0);
        assertEquals(Status.BLOQUEADO, t.status());
        assertEquals(Prioridade.ALTA, t.prioridade());
        assertEquals("aguardando Omne", t.motivoBloqueio());
        assertEquals(List.of("daily"), t.tags());
    }

    @Test
    void criarTarefaSemCamposCalculados() throws IOException {
        status = 201;
        resposta = TAREFA;
        client.criarTarefa(new TarefaRequest(1L, null, "Pedir VPN", null, Status.A_FAZER, null, Prioridade.MEDIA, List.of(), null));
        assertEquals("POST", metodo.get());
        JsonNode json = new ObjectMapper().readTree(corpo.get());
        assertFalse(json.has("id"));
        assertFalse(json.has("ordem"));
        assertFalse(json.has("projetoId"), "nulos nao sao enviados");
        assertEquals("A_FAZER", json.get("status").asText());
    }

    @Test
    void moverEnviaStatusEAntesDe() throws IOException {
        resposta = TAREFA;
        client.moverTarefa(5, Status.BLOQUEADO, 9L, "aguardando Omne");
        assertEquals("/api/tarefas/5/mover", caminho.get());
        JsonNode json = new ObjectMapper().readTree(corpo.get());
        assertEquals("BLOQUEADO", json.get("status").asText());
        assertEquals(9, json.get("antesDeId").asLong());
        assertEquals("aguardando Omne", json.get("motivoBloqueio").asText());

        client.moverTarefa(5, Status.FEITO, null, null);
        JsonNode fim = new ObjectMapper().readTree(corpo.get());
        assertFalse(fim.has("antesDeId"), "null = fim da coluna");
    }

    @Test
    void excluirEConflito() {
        status = 204;
        client.excluirTarefa(5);
        assertEquals("DELETE", metodo.get());
        status = 409;
        resposta = "{\"erro\":\"Squad tem 2 projeto(s) e 3 tarefa(s); arquive ou mova antes de excluir\"}";
        ApiException e = assertThrows(ApiException.class, () -> client.excluirSquad(1));
        assertEquals(409, e.status());
        assertTrue(e.getMessage().startsWith("Squad tem"));
    }

    @Test
    void atualizarSquad() throws IOException {
        resposta = "{\"id\":1,\"slug\":\"guardian\",\"nome\":\"Guardian\",\"ordem\":1,\"arquivado\":true,\"contagem\":{}}";
        client.atualizarSquad(1, new SquadRequest("guardian", "Guardian", 1, true));
        assertEquals("PUT", metodo.get());
        assertTrue(new ObjectMapper().readTree(corpo.get()).get("arquivado").asBoolean());
    }
}
