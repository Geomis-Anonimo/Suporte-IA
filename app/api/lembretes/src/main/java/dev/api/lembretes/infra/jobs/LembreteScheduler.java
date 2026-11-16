package dev.api.lembretes.infra.jobs;

import dev.api.lembretes.core.application.DispararLembretesVencidosService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.scheduling.annotation.Scheduled;

/** Adaptador de entrada agendado: so dispara o caso de uso. */
public class LembreteScheduler {
    private static final Logger LOG = Logger.getLogger(LembreteScheduler.class.getName());

    private final DispararLembretesVencidosService disparar;

    public LembreteScheduler(DispararLembretesVencidosService disparar) { this.disparar = disparar; }

    @Scheduled(fixedDelayString = "${lembretes.job.intervalo-ms}", initialDelay = 5000)
    public void verificar() {
        try {
            int enviados = disparar.executar();
            if (enviados > 0) LOG.info(enviados + " aviso(s) enviado(s)");
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro no job de lembretes", e);
        }
    }
}
