package dev.api.tarefas.presentation.dto;

import dev.api.tarefas.core.application.Dados;
import dev.api.tarefas.core.domain.Projeto;
import dev.api.tarefas.core.domain.StatusTarefa;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** Corpo e resposta de /api/projetos. */
public final class ProjetoDtos {
    private ProjetoDtos() { }

    public record ProjetoRequest(@NotNull Long squadId, @NotBlank String slug, @NotBlank String nome, String descricao,
                                 String contextoMd, Boolean arquivado) {
        public Dados.ProjetoDados paraDados() {
            return new Dados.ProjetoDados(squadId, slug, nome, descricao, contextoMd, Boolean.TRUE.equals(arquivado));
        }
    }

    public record ProjetoResponse(Long id, long squadId, String squadSlug, String slug, String nome, String descricao,
                                  String contextoMd, boolean arquivado, Map<StatusTarefa, Long> contagem) {
        public static ProjetoResponse de(Dados.ProjetoVisao v) {
            Projeto p = v.projeto();
            return new ProjetoResponse(p.id(), p.squadId(), v.squadSlug(), p.slug(), p.nome(), p.descricao(),
                    p.contextoMd(), p.arquivado(), v.contagem());
        }
    }
}
