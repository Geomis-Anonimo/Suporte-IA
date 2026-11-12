package dev.lembretes.lembrete.core.application;

import dev.lembretes.lembrete.core.application.port.LembreteRepository;
import dev.lembretes.lembrete.core.domain.Lembrete;
import java.util.List;

public class ConsultarLembretesService {
    private final LembreteRepository repo;

    public ConsultarLembretesService(LembreteRepository repo) { this.repo = repo; }

    public List<Lembrete> listar(Boolean ativo) { return repo.listar(ativo); }

    public Lembrete obter(long id) {
        return repo.buscarPorId(id).orElseThrow(() -> new LembreteNaoEncontradoException(id));
    }
}
