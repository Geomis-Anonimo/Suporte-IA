package dev.api.lembretes.core.application;

import dev.api.shared.core.DadosInvalidosException;

import dev.api.lembretes.core.application.port.LembreteRepository;
import dev.api.shared.core.port.RelogioPort;
import dev.api.lembretes.core.domain.Lembrete;
import java.time.Instant;

public class CriarLembreteService {
    private final LembreteRepository repo;
    private final RelogioPort relogio;
    private final int antecedenciaPadraoMin;

    public CriarLembreteService(LembreteRepository repo, RelogioPort relogio, int antecedenciaPadraoMin) {
        this.repo = repo;
        this.relogio = relogio;
        this.antecedenciaPadraoMin = antecedenciaPadraoMin;
    }

    public Lembrete executar(LembreteComando cmd) {
        return repo.salvar(montar(null, null, cmd));
    }

    /** Usado tambem pela atualizacao: recalcula a proxima ocorrencia a partir de agora. */
    Lembrete montar(Long id, Instant criadoEm, LembreteComando cmd) {
        Instant agora = relogio.agora();
        Instant ocorrencia = cmd.recorrencia().aPartirDe(agora, relogio.fuso())
                .orElseThrow(() -> new DadosInvalidosException("A data/hora informada ja passou"));
        int antecedencia = cmd.antecedenciaMin() == null ? antecedenciaPadraoMin : cmd.antecedenciaMin();
        try {
            return new Lembrete(id, cmd.titulo(), cmd.descricao(), cmd.mensagem(), cmd.recorrencia(), antecedencia,
                    cmd.contextoMd(), cmd.participantes(), ocorrencia, true, criadoEm == null ? agora : criadoEm);
        } catch (IllegalArgumentException e) {
            throw new DadosInvalidosException(e.getMessage());
        }
    }
}
