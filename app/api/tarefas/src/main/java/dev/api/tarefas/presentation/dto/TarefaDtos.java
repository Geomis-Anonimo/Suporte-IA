package dev.api.tarefas.presentation.dto;

import dev.api.tarefas.core.application.Dados;
import dev.api.tarefas.core.domain.Prioridade;
import dev.api.tarefas.core.domain.StatusTarefa;
import dev.api.tarefas.core.domain.Tarefa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/** Corpo e resposta de /api/tarefas. */
public final class TarefaDtos {
    private TarefaDtos() { }

    /** POST/PUT. Sem campos calculados (ordem, datas): a API recusa campos desconhecidos. */
    public record TarefaRequest(@NotNull Long squadId, Long projetoId, @NotBlank String titulo, String descricao,
                                StatusTarefa status, String motivoBloqueio, Prioridade prioridade, List<String> tags,
                                String link) {
        public Dados.TarefaDados paraDados() {
            return new Dados.TarefaDados(squadId, projetoId, titulo, descricao, status, motivoBloqueio, prioridade, tags, link);
        }
    }

    /** POST /api/tarefas/{id}/mover. {@code antesDeId} nulo = fim da coluna. */
    public record MoverRequest(@NotNull StatusTarefa status, Long antesDeId, String motivoBloqueio) {
        public Dados.Movimento paraDados() {
            return new Dados.Movimento(status, antesDeId, motivoBloqueio);
        }
    }

    public record TarefaResponse(Long id, long squadId, String squadSlug, Long projetoId, String projetoSlug,
                                 String projetoNome, String titulo, String descricao, StatusTarefa status,
                                 String motivoBloqueio, Prioridade prioridade, List<String> tags, String link, double ordem,
                                 ZonedDateTime criadaEm, ZonedDateTime atualizadaEm, ZonedDateTime concluidaEm) {
        public static TarefaResponse de(Dados.TarefaVisao v, ZoneId fuso) {
            Tarefa t = v.tarefa();
            return new TarefaResponse(t.id(), t.squadId(), v.squadSlug(), t.projetoId(), v.projetoSlug(), v.projetoNome(),
                    t.titulo(), t.descricao(), t.status(), t.motivoBloqueio(), t.prioridade(), t.tags(), t.link(), t.ordem(),
                    zona(t.criadaEm(), fuso), zona(t.atualizadaEm(), fuso), zona(t.concluidaEm(), fuso));
        }

        private static ZonedDateTime zona(Instant i, ZoneId fuso) {
            return i == null ? null : i.atZone(fuso);
        }
    }
}
