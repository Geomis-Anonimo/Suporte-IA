package dev.lembretes.lembrete.presentation.dto;

import dev.lembretes.lembrete.core.application.LembreteComando;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Corpo de POST/PUT /api/lembretes. {@code antecedenciaMin} omitido = padrao (5 min). */
public record LembreteRequest(
        @NotBlank String titulo,
        String descricao,
        @NotNull @Valid RecorrenciaDto recorrencia,
        @Min(0) Integer antecedenciaMin,
        String contextoMd,
        @Valid List<ParticipanteDto> participantes) {

    public LembreteComando paraComando() {
        return new LembreteComando(titulo, descricao, recorrencia.paraDominio(), antecedenciaMin, contextoMd,
                participantes == null ? List.of() : participantes.stream().map(ParticipanteDto::paraDominio).toList());
    }
}
