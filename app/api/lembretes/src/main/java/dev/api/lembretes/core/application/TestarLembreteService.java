package dev.api.lembretes.core.application;

import dev.api.shared.core.DadosInvalidosException;

import dev.api.lembretes.core.application.port.LembreteRepository;
import dev.api.shared.core.port.NotificadorPort;
import dev.api.shared.core.port.NotificadorPort.NotificacaoException;
import dev.api.shared.core.port.RelogioPort;
import dev.api.lembretes.core.domain.Lembrete;

/** Envia o aviso agora, sem alterar o lembrete (para testar o Discord e o texto). */
public class TestarLembreteService {
    private final LembreteRepository repo;
    private final NotificadorPort notificador;
    private final MontadorAviso montador;
    private final RelogioPort relogio;

    public TestarLembreteService(LembreteRepository repo, NotificadorPort notificador,
                                 MontadorAviso montador, RelogioPort relogio) {
        this.repo = repo;
        this.notificador = notificador;
        this.montador = montador;
        this.relogio = relogio;
    }

    public String executar(long id) throws NotificacaoException {
        Lembrete l = repo.buscarPorId(id).orElseThrow(() -> new LembreteNaoEncontradoException(id));
        if (l.proximaOcorrencia() == null) throw new DadosInvalidosException("Lembrete encerrado, nada a avisar");
        String msg = montador.montar(l, relogio.agora());
        notificador.enviar("🧪 (teste) " + msg);
        return msg;
    }
}
