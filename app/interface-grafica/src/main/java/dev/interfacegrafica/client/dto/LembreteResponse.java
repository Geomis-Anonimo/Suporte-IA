package dev.interfacegrafica.client.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Lembrete como a API devolve. Campos desconhecidos sao ignorados na leitura. */
public record LembreteResponse(
        Long id,
        String titulo,
        String descricao,
        String mensagem,
        RecorrenciaDto recorrencia,
        int antecedenciaMin,
        String contextoMd,
        List<ParticipanteDto> participantes,
        OffsetDateTime proximaOcorrencia,
        OffsetDateTime proximoDisparo,
        boolean ativo) {

    public List<ParticipanteDto> participantesOuVazio() {
        return participantes == null ? List.of() : participantes;
    }
}
