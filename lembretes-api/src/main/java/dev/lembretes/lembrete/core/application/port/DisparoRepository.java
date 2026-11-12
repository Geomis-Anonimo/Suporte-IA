package dev.lembretes.lembrete.core.application.port;

import dev.lembretes.lembrete.core.domain.StatusDisparo;
import java.time.Instant;

public interface DisparoRepository {
    /** Registra o disparo. Devolve false se essa ocorrencia ja tinha sido registrada. */
    boolean registrar(long lembreteId, Instant ocorrencia, Instant enviadoEm, StatusDisparo status, String detalhe);
}
