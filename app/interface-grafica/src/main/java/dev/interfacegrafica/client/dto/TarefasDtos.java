package dev.interfacegrafica.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTOs das rotas /api/squads, /api/projetos e /api/tarefas.
 * Requests NAO levam campos calculados (a api recusa campos desconhecidos).
 */
public final class TarefasDtos {
    private TarefasDtos() { }

    /** Colunas do board, na ordem de exibicao. */
    public enum Status {
        A_FAZER("A Fazer"), FAZENDO("Fazendo"), BLOQUEADO("Bloqueado"), FEITO("Feito");

        public final String rotulo;

        Status(String rotulo) { this.rotulo = rotulo; }
    }

    public enum Prioridade {
        ALTA("Alta"), MEDIA("Média"), BAIXA("Baixa");

        public final String rotulo;

        Prioridade(String rotulo) { this.rotulo = rotulo; }
    }

    // ------------------------------------------------------------- squads

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SquadRequest(String slug, String nome, Integer ordem, Boolean arquivado) { }

    public record SquadDto(Long id, String slug, String nome, int ordem, boolean arquivado, Map<Status, Long> contagem) {
        /** Tarefas nao concluidas. */
        public long abertas() { return TarefasDtos.abertas(contagem); }
    }

    // ------------------------------------------------------------- projetos

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProjetoRequest(Long squadId, String slug, String nome, String descricao, String contextoMd,
                                 Boolean arquivado) { }

    public record ProjetoDto(Long id, long squadId, String squadSlug, String slug, String nome, String descricao,
                             String contextoMd, boolean arquivado, Map<Status, Long> contagem) {
        public long abertas() { return TarefasDtos.abertas(contagem); }
    }

    // ------------------------------------------------------------- tarefas

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TarefaRequest(Long squadId, Long projetoId, String titulo, String descricao, Status status,
                                String motivoBloqueio, Prioridade prioridade, List<String> tags, String link) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MoverRequest(Status status, Long antesDeId, String motivoBloqueio) { }

    public record TarefaDto(Long id, long squadId, String squadSlug, Long projetoId, String projetoSlug, String projetoNome,
                            String titulo, String descricao, Status status, String motivoBloqueio, Prioridade prioridade,
                            List<String> tags, String link, double ordem, OffsetDateTime criadaEm,
                            OffsetDateTime atualizadaEm, OffsetDateTime concluidaEm) {
        public List<String> tagsOuVazio() { return tags == null ? List.of() : tags; }

        /** Mesmo conteudo editavel, para PUT. */
        public TarefaRequest paraRequest() {
            return new TarefaRequest(squadId, projetoId, titulo, descricao, status, motivoBloqueio, prioridade, tags, link);
        }
    }

    static long abertas(Map<Status, Long> contagem) {
        if (contagem == null) return 0;
        return contagem.entrySet().stream().filter(e -> e.getKey() != Status.FEITO).mapToLong(Map.Entry::getValue).sum();
    }
}
