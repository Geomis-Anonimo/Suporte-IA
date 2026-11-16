package dev.api.lembretes.presentation.dto;

import dev.api.lembretes.core.application.LembreteComando;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Corpo de POST/PUT /api/lembretes. {@code antecedenciaMin} omitido = padrao (5 min).
 * {@code mensagem} e o texto em linguagem natural do aviso (escrito pelo Claude a partir do contexto).
 */
public record LembreteRequest(
        @NotBlank String titulo,
        String descricao,
        String mensagem,
        @NotNull @Valid RecorrenciaDto recorrencia,
        @Min(0) Integer antecedenciaMin,
        String contextoMd,
        @Valid List<ParticipanteDto> participantes) {

    public LembreteComando paraComando() {
        return new LembreteComando(titulo, descricao, mensagem, recorrencia.paraDominio(), antecedenciaMin, contextoMd,
                participantes == null ? List.of() : participantes.stream().map(ParticipanteDto::paraDominio).toList());
    }
}
