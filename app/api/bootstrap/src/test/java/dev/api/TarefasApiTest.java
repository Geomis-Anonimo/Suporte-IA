package dev.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Rotas do modulo tarefas pela aplicacao inteira (status HTTP e corpo {"erro"}). */
@SpringBootTest
@AutoConfigureMockMvc
class TarefasApiTest {

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry r) throws Exception {
        Path db = Files.createTempFile("tarefas-api-teste", ".db");
        r.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
        r.add("lembretes.job.intervalo-ms", () -> "3600000");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private ResultActions postar(String rota, String corpo) throws Exception {
        return mvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private long id(ResultActions r) throws Exception {
        JsonNode n = json.readTree(r.andReturn().getResponse().getContentAsString());
        return n.get("id").asLong();
    }

    @Test
    void fluxoCompleto() throws Exception {
        long squad = id(postar("/api/squads", "{\"slug\":\"guardian\",\"nome\":\"Guardian\"}").andExpect(status().isCreated()));
        postar("/api/squads", "{\"slug\":\"guardian\",\"nome\":\"De novo\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro", containsString("guardian")));
        long outro = id(postar("/api/squads", "{\"slug\":\"tela-web\",\"nome\":\"Tela Web\"}").andExpect(status().isCreated()));

        long projeto = id(postar("/api/projetos", "{\"squadId\":" + squad + ",\"slug\":\"guardian_whatsapp\",\"nome\":\"guardian_whatsapp\","
                + "\"contextoMd\":\"guardian/projects/guardian_whatsapp/README.md\"}").andExpect(status().isCreated()));

        long t1 = id(postar("/api/tarefas", "{\"squadId\":" + squad + ",\"projetoId\":" + projeto + ",\"titulo\":\"Validar template\","
                + "\"prioridade\":\"ALTA\",\"tags\":[\"Daily\"],\"link\":\"guardian/dailies/2026-11-16-daily.md\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("A_FAZER"))
                .andExpect(jsonPath("$.projetoSlug").value("guardian_whatsapp"))
                .andExpect(jsonPath("$.tags[0]").value("daily")));
        long t2 = id(postar("/api/tarefas", "{\"squadId\":" + squad + ",\"titulo\":\"Pedir VPN\"}").andExpect(status().isCreated()));

        // mover t2 para antes de t1 e depois bloquear t1
        postar("/api/tarefas/" + t2 + "/mover", "{\"status\":\"A_FAZER\",\"antesDeId\":" + t1 + "}").andExpect(status().isOk());
        mvc.perform(get("/api/tarefas").param("squadId", "" + squad).param("status", "A_FAZER"))
                .andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[0].titulo").value("Pedir VPN"));
        postar("/api/tarefas/" + t1 + "/mover", "{\"status\":\"BLOQUEADO\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro", containsString("motivoBloqueio")));
        postar("/api/tarefas/" + t1 + "/mover", "{\"status\":\"BLOQUEADO\",\"motivoBloqueio\":\"aguardando Omne\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.motivoBloqueio").value("aguardando Omne"));

        // filtros
        mvc.perform(get("/api/tarefas").param("projetoId", "0")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/tarefas").param("busca", "VALIDAR")).andExpect(jsonPath("$[0].id").value(t1));
        mvc.perform(get("/api/tarefas").param("status", "INVALIDO")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/squads")).andExpect(jsonPath("$[0].contagem.BLOQUEADO").value(1));

        // validacoes
        postar("/api/tarefas", "{\"squadId\":" + outro + ",\"projetoId\":" + projeto + ",\"titulo\":\"X\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erro", containsString("nao pertence")));
        postar("/api/tarefas", "{\"squadId\":" + squad + ",\"titulo\":\"X\",\"ordem\":1}")
                .andExpect(status().isBadRequest());
        postar("/api/tarefas", "{\"squadId\":" + squad + ",\"titulo\":\" \"}").andExpect(status().isBadRequest());
        mvc.perform(get("/api/tarefas/99999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro", containsString("nao encontrada")));

        // exclusoes
        mvc.perform(delete("/api/squads/" + squad)).andExpect(status().isConflict());
        mvc.perform(delete("/api/projetos/" + projeto)).andExpect(status().isConflict());
        mvc.perform(delete("/api/tarefas/" + t1)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/projetos/" + projeto)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/squads/" + outro)).andExpect(status().isNoContent());
    }
}
