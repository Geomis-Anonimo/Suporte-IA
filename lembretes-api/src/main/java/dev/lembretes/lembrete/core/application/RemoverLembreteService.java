package dev.lembretes.lembrete.core.application;

import dev.lembretes.lembrete.core.application.port.LembreteRepository;

public class RemoverLembreteService {
    private final LembreteRepository repo;

    public RemoverLembreteService(LembreteRepository repo) { this.repo = repo; }

    public void executar(long id) {
        if (!repo.remover(id)) throw new LembreteNaoEncontradoException(id);
    }
}
