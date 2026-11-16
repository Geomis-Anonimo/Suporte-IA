package dev.api.tarefas.core.application;

import dev.api.tarefas.core.domain.Prioridade;
import dev.api.tarefas.core.domain.Projeto;
import dev.api.tarefas.core.domain.Squad;
import dev.api.tarefas.core.domain.StatusTarefa;
import dev.api.tarefas.core.domain.Tarefa;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Entradas (comandos) e saidas (visoes) dos casos de uso do modulo tarefas. */
public final class Dados {
    private Dados() { }

    // ------------------------------------------------------------- entradas

    /** @param ordem null = no fim */
    public record SquadDados(String slug, String nome, Integer ordem, boolean arquivado) { }

    public record ProjetoDados(Long squadId, String slug, String nome, String descricao, String contextoMd,
                               boolean arquivado) { }

    public record TarefaDados(Long squadId, Long projetoId, String titulo, String descricao, StatusTarefa status,
                              String motivoBloqueio, Prioridade prioridade, List<String> tags, String link) { }

    /** @param antesDeId null = no fim da coluna */
    public record Movimento(StatusTarefa status, Long antesDeId, String motivoBloqueio) { }

    /**
     * Filtro completo da listagem.
     *
     * @param projetoId null = qualquer; 0 = so as sem projeto
     * @param busca     titulo/descricao/tags/motivo, sem diferenciar maiusculas nem acentos
     */
    public record FiltroTarefas(Long squadId, Long projetoId, List<StatusTarefa> status, Prioridade prioridade,
                                String tag, String busca, java.time.Instant concluidasDesde) { }

    // ------------------------------------------------------------- saidas

    /** Quantidade de tarefas por status, sempre com as 4 chaves. */
    public static Map<StatusTarefa, Long> contagem(Map<StatusTarefa, Long> parcial) {
        Map<StatusTarefa, Long> c = new EnumMap<>(StatusTarefa.class);
        for (StatusTarefa s : StatusTarefa.values()) c.put(s, parcial == null ? 0L : parcial.getOrDefault(s, 0L));
        return c;
    }

    public record SquadVisao(Squad squad, Map<StatusTarefa, Long> contagem) { }

    public record ProjetoVisao(Projeto projeto, String squadSlug, Map<StatusTarefa, Long> contagem) { }

    public record TarefaVisao(Tarefa tarefa, String squadSlug, String projetoSlug, String projetoNome) { }
}
