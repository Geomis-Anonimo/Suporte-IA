package dev.api.lembretes.core.application;

import dev.api.lembretes.core.application.port.DisparoRepository;
import dev.api.lembretes.core.application.port.LembreteRepository;
import dev.api.shared.core.port.NotificadorPort;
import dev.api.shared.core.port.NotificadorPort.NotificacaoException;
import dev.api.shared.core.port.RelogioPort;
import dev.api.lembretes.core.domain.Lembrete;
import dev.api.lembretes.core.domain.StatusDisparo;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Caso de uso chamado pelo job a cada minuto: avisa tudo que esta a ate
 * (antecedencia + janela extra) minutos de acontecer.
 */
public class DispararLembretesVencidosService {
    private static final Logger LOG = Logger.getLogger(DispararLembretesVencidosService.class.getName());

    private final LembreteRepository repo;
    private final DisparoRepository disparos;
    private final NotificadorPort notificador;
    private final MontadorAviso montador;
    private final RelogioPort relogio;
    private final Duration janelaExtra;
    private final Duration toleranciaAtraso;

    public DispararLembretesVencidosService(LembreteRepository repo, DisparoRepository disparos,
                                            NotificadorPort notificador, MontadorAviso montador, RelogioPort relogio,
                                            Duration janelaExtra, Duration toleranciaAtraso) {
        this.repo = repo;
        this.disparos = disparos;
        this.notificador = notificador;
        this.montador = montador;
        this.relogio = relogio;
        this.janelaExtra = janelaExtra;
        this.toleranciaAtraso = toleranciaAtraso;
    }

    /** @return quantos avisos foram enviados */
    public int executar() {
        Instant agora = relogio.agora();
        List<Lembrete> vencidos = repo.buscarComDisparoAte(agora.plus(janelaExtra));
        int enviados = 0;
        for (Lembrete l : vencidos) {
            Instant ocorrencia = l.proximaOcorrencia();
            if (agora.isAfter(ocorrencia.plus(toleranciaAtraso))) {
                disparos.registrar(l.id(), ocorrencia, agora, StatusDisparo.EXPIRADO, "passou da tolerancia");
                avancar(l, agora);
                continue;
            }
            try {
                notificador.enviar(montador.montar(l, agora));
            } catch (NotificacaoException e) {
                // mantem o lembrete pendente: tenta de novo no proximo ciclo
                LOG.log(Level.WARNING, "Falha ao avisar lembrete " + l.id() + ": " + e.getMessage());
                continue;
            }
            disparos.registrar(l.id(), ocorrencia, agora, StatusDisparo.ENVIADO, null);
            avancar(l, agora);
            enviados++;
        }
        return enviados;
    }

    private void avancar(Lembrete l, Instant agora) {
        Instant ref = l.proximaOcorrencia().isAfter(agora) ? l.proximaOcorrencia() : agora;
        var proxima = l.recorrencia().apos(ref, relogio.fuso());
        repo.salvar(proxima.map(p -> l.comOcorrencia(p, true)).orElseGet(() -> l.comOcorrencia(null, false)));
    }
}
