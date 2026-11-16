package dev.api.tarefas.core.application.port;

import dev.api.tarefas.core.domain.Prioridade;
import dev.api.tarefas.core.domain.StatusTarefa;
import dev.api.tarefas.core.domain.Tarefa;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface TarefaRepository {

    /**
     * Filtros que o banco resolve. Busca por texto e tag ficam no servico (sem acento, tags em JSON).
     *
     * @param projetoId       null = qualquer; 0 = so as sem projeto
     * @param status          vazio = todos
     * @param concluidasDesde se informado, tarefas FEITO concluidas antes disso ficam de fora
     */
    record Filtro(Long squadId, Long projetoId, Set<StatusTarefa> status, Prioridade prioridade, Instant concluidasDesde) {
        public Filtro {
            status = status == null ? Set.of() : Set.copyOf(status);
        }
    }

    Tarefa salvar(Tarefa tarefa);

    Optional<Tarefa> buscarPorId(long id);

    /** Ordenado por status (ordem do enum), depois ordem, depois id. */
    List<Tarefa> listar(Filtro filtro);

    /** Tarefas de uma coluna (squad + status), por ordem. */
    List<Tarefa> listarColuna(long squadId, StatusTarefa status);

    /** Maior ordem da coluna, ou 0 se vazia. */
    double maiorOrdem(long squadId, StatusTarefa status);

    void atualizarOrdens(Map<Long, Double> ordemPorId);

    int contarPorSquad(long squadId);

    int contarPorProjeto(long projetoId);

    /** squadId -> (status -> quantidade). */
    Map<Long, Map<StatusTarefa, Long>> contagemPorSquad();

    /** projetoId -> (status -> quantidade). */
    Map<Long, Map<StatusTarefa, Long>> contagemPorProjeto();

    boolean remover(long id);
}
