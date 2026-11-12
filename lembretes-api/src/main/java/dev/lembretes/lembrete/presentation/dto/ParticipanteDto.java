package dev.lembretes.lembrete.presentation.dto;

import dev.lembretes.lembrete.core.domain.Participante;
import jakarta.validation.constraints.NotBlank;

public record ParticipanteDto(@NotBlank String nome, String detalhe) {
    public Participante paraDominio() { return new Participante(nome, detalhe); }

    public static ParticipanteDto de(Participante p) { return new ParticipanteDto(p.nome(), p.detalhe()); }
}
