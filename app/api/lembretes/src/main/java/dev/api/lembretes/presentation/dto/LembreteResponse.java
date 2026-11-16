package dev.api.lembretes.presentation.dto;

import dev.api.lembretes.core.domain.Lembrete;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public record LembreteResponse(
        Long id,
        String titulo,
        String descricao,
        String mensagem,
        RecorrenciaDto recorrencia,
        int antecedenciaMin,
        String contextoMd,
        List<ParticipanteDto> participantes,
        ZonedDateTime proximaOcorrencia,
        ZonedDateTime proximoDisparo,
        boolean ativo) {

    public static LembreteResponse de(Lembrete l, ZoneId fuso) {
        return new LembreteResponse(l.id(), l.titulo(), l.descricao(), l.mensagem(), RecorrenciaDto.de(l.recorrencia()),
                l.antecedenciaMin(), l.contextoMd(), l.participantes().stream().map(ParticipanteDto::de).toList(),
                l.proximaOcorrencia() == null ? null : l.proximaOcorrencia().atZone(fuso),
                l.proximoDisparo() == null ? null : l.proximoDisparo().atZone(fuso), l.ativo());
    }
}
