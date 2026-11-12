package dev.lembretes.lembrete.infra.persistence;

import dev.lembretes.lembrete.core.application.port.DisparoRepository;
import dev.lembretes.lembrete.core.domain.StatusDisparo;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;

public class JdbcDisparoRepository implements DisparoRepository {
    private final JdbcClient jdbc;

    public JdbcDisparoRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean registrar(long lembreteId, Instant ocorrencia, Instant enviadoEm, StatusDisparo status, String detalhe) {
        return jdbc.sql("INSERT OR IGNORE INTO disparo (lembrete_id, ocorrencia, enviado_em, status, detalhe) "
                        + "VALUES (?,?,?,?,?)")
                .params(lembreteId, ocorrencia.toEpochMilli(), enviadoEm.toEpochMilli(), status.name(), detalhe)
                .update() > 0;
    }
}
