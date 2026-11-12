package dev.lembretes.lembrete.core.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Lembrete de uma reuniao/rotina.
 *
 * @param proximaOcorrencia quando o evento acontece (nulo se encerrado)
 * @param contextoMd        caminho do .md que contextualiza o assunto (opcional)
 */
public record Lembrete(
        Long id,
        String titulo,
        String descricao,
        Recorrencia recorrencia,
        int antecedenciaMin,
        String contextoMd,
        List<Participante> participantes,
        Instant proximaOcorrencia,
        boolean ativo,
        Instant criadoEm) {

    public Lembrete {
        if (titulo == null || titulo.isBlank()) throw new IllegalArgumentException("titulo e obrigatorio");
        if (recorrencia == null) throw new IllegalArgumentException("recorrencia e obrigatoria");
        if (antecedenciaMin < 0) throw new IllegalArgumentException("antecedenciaMin nao pode ser negativa");
        participantes = participantes == null ? List.of() : List.copyOf(participantes);
    }

    /** Momento em que o aviso deve sair (ocorrencia menos antecedencia). */
    public Instant proximoDisparo() {
        return proximaOcorrencia == null ? null : proximaOcorrencia.minus(Duration.ofMinutes(antecedenciaMin));
    }

    public Lembrete comId(long novoId) {
        return new Lembrete(novoId, titulo, descricao, recorrencia, antecedenciaMin, contextoMd,
                participantes, proximaOcorrencia, ativo, criadoEm);
    }

    public Lembrete comOcorrencia(Instant ocorrencia, boolean ativo) {
        return new Lembrete(id, titulo, descricao, recorrencia, antecedenciaMin, contextoMd,
                participantes, ocorrencia, ativo, criadoEm);
    }
}
