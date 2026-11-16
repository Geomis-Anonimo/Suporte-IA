package dev.api.tarefas.core.application.port;

import dev.api.tarefas.core.domain.Squad;
import java.util.List;
import java.util.Optional;

public interface SquadRepository {
    /** Insere (id nulo) ou atualiza. */
    Squad salvar(Squad squad);

    Optional<Squad> buscarPorId(long id);

    Optional<Squad> buscarPorSlug(String slug);

    /** Ordenado por ordem, depois nome. */
    List<Squad> listar(boolean incluirArquivados);

    int proximaOrdem();

    boolean remover(long id);
}
