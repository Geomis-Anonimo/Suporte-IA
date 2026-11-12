package dev.lembretes.lembrete.core.application;

import dev.lembretes.lembrete.core.application.port.LembreteRepository;
import dev.lembretes.lembrete.core.domain.Lembrete;

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
