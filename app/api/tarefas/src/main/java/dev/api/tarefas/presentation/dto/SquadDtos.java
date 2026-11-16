package dev.api.tarefas.presentation.dto;

import dev.api.tarefas.core.application.Dados;
import dev.api.tarefas.core.domain.Squad;
import dev.api.tarefas.core.domain.StatusTarefa;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/** Corpo e resposta de /api/squads. */
public final class SquadDtos {
    private SquadDtos() { }

    /** POST/PUT. {@code ordem} omitida = no fim (POST) ou mantida (PUT); {@code arquivado} omitido = false. */
    public record SquadRequest(@NotBlank String slug, @NotBlank String nome, Integer ordem, Boolean arquivado) {
        public Dados.SquadDados paraDados() {
            return new Dados.SquadDados(slug, nome, ordem, Boolean.TRUE.equals(arquivado));
        }
    }

    public record SquadResponse(Long id, String slug, String nome, int ordem, boolean arquivado,
                                Map<StatusTarefa, Long> contagem) {
        public static SquadResponse de(Dados.SquadVisao v) {
            Squad s = v.squad();
            return new SquadResponse(s.id(), s.slug(), s.nome(), s.ordem(), s.arquivado(), v.contagem());
        }
    }
}
