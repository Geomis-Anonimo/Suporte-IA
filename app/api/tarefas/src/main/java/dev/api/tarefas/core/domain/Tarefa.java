package dev.api.tarefas.core.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tarefa (card do board).
 *
 * @param projetoId      opcional: tarefa so do squad, sem projeto
 * @param motivoBloqueio obrigatorio quando BLOQUEADO; sempre null nos outros status
 * @param ordem          posicao dentro da coluna (squad + status); menor = mais acima
 * @param concluidaEm    preenchido so em FEITO
 */
public record Tarefa(
        Long id,
        long squadId,
        Long projetoId,
        String titulo,
        String descricao,
        StatusTarefa status,
        String motivoBloqueio,
        Prioridade prioridade,
        List<String> tags,
        String link,
        double ordem,
        Instant criadaEm,
        Instant atualizadaEm,
        Instant concluidaEm) {

    public static final int MAX_TITULO = 200;
    public static final int MAX_TAG = 30;

    public Tarefa {
        if (titulo == null || titulo.isBlank()) throw new IllegalArgumentException("titulo e obrigatorio");
        titulo = titulo.strip();
        if (titulo.length() > MAX_TITULO) throw new IllegalArgumentException("titulo passa de " + MAX_TITULO + " caracteres");
        descricao = Textos.limpar(descricao);
        status = status == null ? StatusTarefa.A_FAZER : status;
        prioridade = prioridade == null ? Prioridade.MEDIA : prioridade;
        motivoBloqueio = Textos.limpar(motivoBloqueio);
        if (status == StatusTarefa.BLOQUEADO && motivoBloqueio == null)
            throw new IllegalArgumentException("motivoBloqueio e obrigatorio quando o status e BLOQUEADO");
        if (status != StatusTarefa.BLOQUEADO) motivoBloqueio = null;
        if (status != StatusTarefa.FEITO) concluidaEm = null;
        tags = normalizarTags(tags);
        link = Textos.limpar(link);
        if (link != null && !Textos.linkValido(link))
            throw new IllegalArgumentException("link deve ser uma URL http(s) ou um caminho .md relativo a docs/");
    }

    /** Minusculas, sem espacos nas pontas, sem repetidas; cada uma ate 30 caracteres e sem espacos internos. */
    public static List<String> normalizarTags(List<String> tags) {
        if (tags == null) return List.of();
        List<String> saida = new ArrayList<>();
        for (String t : tags) {
            if (t == null || t.isBlank()) continue;
            String tag = t.strip().toLowerCase(Locale.ROOT);
            if (tag.length() > MAX_TAG) throw new IllegalArgumentException("tag passa de " + MAX_TAG + " caracteres: " + tag);
            if (tag.chars().anyMatch(Character::isWhitespace))
                throw new IllegalArgumentException("tag nao pode ter espacos: " + tag);
            if (!saida.contains(tag)) saida.add(tag);
        }
        return List.copyOf(saida);
    }

    /**
     * Mesma tarefa em outro status: aplica as regras de concluidaEm (entra ao virar FEITO, sai ao deixar FEITO)
     * e de motivoBloqueio.
     */
    public Tarefa comStatus(StatusTarefa novo, String motivo, double novaOrdem, Instant agora) {
        Instant concluida = novo == StatusTarefa.FEITO ? (status == StatusTarefa.FEITO ? concluidaEm : agora) : null;
        return new Tarefa(id, squadId, projetoId, titulo, descricao, novo, motivo, prioridade, tags, link, novaOrdem,
                criadaEm, agora, concluida);
    }

    public Tarefa comId(long novoId) {
        return new Tarefa(novoId, squadId, projetoId, titulo, descricao, status, motivoBloqueio, prioridade, tags, link,
                ordem, criadaEm, atualizadaEm, concluidaEm);
    }

    public Tarefa comOrdem(double novaOrdem) {
        return new Tarefa(id, squadId, projetoId, titulo, descricao, status, motivoBloqueio, prioridade, tags, link,
                novaOrdem, criadaEm, atualizadaEm, concluidaEm);
    }
}
