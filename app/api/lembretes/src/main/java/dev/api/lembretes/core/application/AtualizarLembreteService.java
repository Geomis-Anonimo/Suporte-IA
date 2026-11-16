package dev.api.lembretes.core.application;

import dev.api.lembretes.core.application.port.LembreteRepository;
import dev.api.lembretes.core.domain.Lembrete;

public class AtualizarLembreteService {
    private final LembreteRepository repo;
    private final CriarLembreteService criador;

    public AtualizarLembreteService(LembreteRepository repo, CriarLembreteService criador) {
        this.repo = repo;
        this.criador = criador;
    }

    public Lembrete executar(long id, LembreteComando cmd) {
        Lembrete atual = repo.buscarPorId(id).orElseThrow(() -> new LembreteNaoEncontradoException(id));
        return repo.salvar(criador.montar(id, atual.criadoEm(), cmd));
    }
}
