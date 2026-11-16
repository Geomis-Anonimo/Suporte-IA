package dev.interfacegrafica.client;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.interfacegrafica.client.dto.TarefasDtos.MoverRequest;
import dev.interfacegrafica.client.dto.TarefasDtos.ProjetoDto;
import dev.interfacegrafica.client.dto.TarefasDtos.ProjetoRequest;
import dev.interfacegrafica.client.dto.TarefasDtos.SquadDto;
import dev.interfacegrafica.client.dto.TarefasDtos.SquadRequest;
import dev.interfacegrafica.client.dto.TarefasDtos.Status;
import dev.interfacegrafica.client.dto.TarefasDtos.TarefaDto;
import dev.interfacegrafica.client.dto.TarefasDtos.TarefaRequest;
import java.time.OffsetDateTime;
import java.util.List;

/** Cliente sincrono do modulo tarefas da api (squads, projetos, tarefas). Sem JavaFX. */
public class TarefasClient extends ApiHttp {
    private static final TypeReference<SquadDto> SQUAD = new TypeReference<>() { };
    private static final TypeReference<ProjetoDto> PROJETO = new TypeReference<>() { };
    private static final TypeReference<TarefaDto> TAREFA = new TypeReference<>() { };

    public TarefasClient(String baseUrl) {
        super(baseUrl);
    }

    // ------------------------------------------------------------- squads

    public List<SquadDto> listarSquads(boolean incluirArquivados) {
        return get("/api/squads" + query("incluirArquivados", incluirArquivados), new TypeReference<List<SquadDto>>() { });
    }

    public SquadDto criarSquad(SquadRequest req) { return post("/api/squads", req, SQUAD); }

    public SquadDto atualizarSquad(long id, SquadRequest req) { return put("/api/squads/" + id, req, SQUAD); }

    public void excluirSquad(long id) { delete("/api/squads/" + id); }

    // ------------------------------------------------------------- projetos

    /** @param squadId null = todos */
    public List<ProjetoDto> listarProjetos(Long squadId, boolean incluirArquivados) {
        return get("/api/projetos" + query("squadId", squadId, "incluirArquivados", incluirArquivados),
                new TypeReference<List<ProjetoDto>>() { });
    }

    public ProjetoDto criarProjeto(ProjetoRequest req) { return post("/api/projetos", req, PROJETO); }

    public ProjetoDto atualizarProjeto(long id, ProjetoRequest req) { return put("/api/projetos/" + id, req, PROJETO); }

    public void excluirProjeto(long id) { delete("/api/projetos/" + id); }

    // ------------------------------------------------------------- tarefas

    /**
     * @param squadId         null = todos
     * @param concluidasDesde null = todas as FEITO; senao, esconde as concluidas antes disso
     */
    public List<TarefaDto> listarTarefas(Long squadId, OffsetDateTime concluidasDesde) {
        return get("/api/tarefas" + query("squadId", squadId, "concluidasDesde", concluidasDesde),
                new TypeReference<List<TarefaDto>>() { });
    }

    public TarefaDto criarTarefa(TarefaRequest req) { return post("/api/tarefas", req, TAREFA); }

    public TarefaDto atualizarTarefa(long id, TarefaRequest req) { return put("/api/tarefas/" + id, req, TAREFA); }

    /** Move para a coluna, antes de {@code antesDeId} (null = fim). */
    public TarefaDto moverTarefa(long id, Status status, Long antesDeId, String motivoBloqueio) {
        return post("/api/tarefas/" + id + "/mover", new MoverRequest(status, antesDeId, motivoBloqueio), TAREFA);
    }

    public void excluirTarefa(long id) { delete("/api/tarefas/" + id); }
}
