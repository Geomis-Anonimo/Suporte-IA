package dev.lembretes.lembrete.core.application.port;

import dev.lembretes.lembrete.core.domain.Lembrete;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LembreteRepository {
    /** Insere (id nulo) ou atualiza. Devolve o lembrete com id. */
    Lembrete salvar(Lembrete lembrete);

    Optional<Lembrete> buscarPorId(long id);

    List<Lembrete> listar(Boolean ativo);

    /** Ativos cujo proximo disparo e menor ou igual a {@code limite}. */
    List<Lembrete> buscarComDisparoAte(Instant limite);

    boolean remover(long id);
}
