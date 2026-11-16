package dev.interfacegrafica.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Corpo de POST/PUT. A API recusa campos desconhecidos, entao este DTO NAO tem id, ativo nem datas calculadas.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LembreteRequest(
        String titulo,
        String descricao,
        String mensagem,
        RecorrenciaDto recorrencia,
        Integer antecedenciaMin,
        String contextoMd,
        List<ParticipanteDto> participantes) {
}
