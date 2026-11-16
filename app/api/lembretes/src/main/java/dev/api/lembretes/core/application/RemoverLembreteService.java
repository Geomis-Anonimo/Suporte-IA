package dev.api.lembretes.core.application;

import dev.api.lembretes.core.application.port.LembreteRepository;

public class RemoverLembreteService {
    private final LembreteRepository repo;

    public RemoverLembreteService(LembreteRepository repo) { this.repo = repo; }

    public void executar(long id) {
        if (!repo.remover(id)) throw new LembreteNaoEncontradoException(id);
    }
}
