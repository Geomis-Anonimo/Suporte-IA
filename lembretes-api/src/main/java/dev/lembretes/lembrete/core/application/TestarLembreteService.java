package dev.lembretes.lembrete.core.application;

import dev.lembretes.lembrete.core.application.port.LembreteRepository;
import dev.lembretes.lembrete.core.application.port.NotificadorPort;
import dev.lembretes.lembrete.core.application.port.NotificadorPort.NotificacaoException;
import dev.lembretes.lembrete.core.application.port.RelogioPort;
import dev.lembretes.lembrete.core.domain.Lembrete;

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
