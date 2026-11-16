package dev.api.tarefas.core.application.port;

import dev.api.tarefas.core.domain.Projeto;
import java.util.List;
import java.util.Optional;

public interface ProjetoRepository {
    Projeto salvar(Projeto projeto);

    Optional<Projeto> buscarPorId(long id);

    Optional<Projeto> buscarPorSlug(long squadId, String slug);

    /** @param squadId null = todos os squads. Ordenado por nome. */
    List<Projeto> listar(Long squadId, boolean incluirArquivados);

    int contarPorSquad(long squadId);

    boolean remover(long id);
}
