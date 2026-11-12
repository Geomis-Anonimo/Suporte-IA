package dev.lembretes.lembrete.infra.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.lembretes.lembrete.core.application.port.LembreteRepository;
import dev.lembretes.lembrete.core.domain.Lembrete;
import dev.lembretes.lembrete.core.domain.Participante;
import dev.lembretes.lembrete.core.domain.Recorrencia;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/** Adaptador SQLite (JDBC) da porta {@link LembreteRepository}. Instantes guardados em epoch millis. */
public class JdbcLembreteRepository implements LembreteRepository {

    private static final TypeReference<List<Participante>> PARTICIPANTES = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JdbcLembreteRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Lembrete salvar(Lembrete l) {
        Object[] rec = colunasRecorrencia(l.recorrencia());
        if (l.id() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.sql("""
                    INSERT INTO lembrete (titulo, descricao, mensagem, rec_tipo, rec_data_hora, rec_hora,
                      rec_dia_semana, rec_dia_mes, antecedencia_min, contexto_md, participantes_json,
                      proxima_ocorrencia, proximo_disparo, ativo, criado_em)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""")
                    .params(l.titulo(), l.descricao(), l.mensagem(), rec[0], rec[1], rec[2], rec[3], rec[4], l.antecedenciaMin(),
                            l.contextoMd(), participantesJson(l), millis(l.proximaOcorrencia()),
                            millis(l.proximoDisparo()), l.ativo() ? 1 : 0, l.criadoEm().toEpochMilli())
                    .update(keys);
            return l.comId(keys.getKey().longValue());
        }
        jdbc.sql("""
                UPDATE lembrete SET titulo=?, descricao=?, mensagem=?, rec_tipo=?, rec_data_hora=?, rec_hora=?,
                  rec_dia_semana=?, rec_dia_mes=?, antecedencia_min=?, contexto_md=?, participantes_json=?,
                  proxima_ocorrencia=?, proximo_disparo=?, ativo=? WHERE id=?""")
                .params(l.titulo(), l.descricao(), l.mensagem(), rec[0], rec[1], rec[2], rec[3], rec[4], l.antecedenciaMin(),
                        l.contextoMd(), participantesJson(l), millis(l.proximaOcorrencia()),
                        millis(l.proximoDisparo()), l.ativo() ? 1 : 0, l.id())
                .update();
        return l;
    }

    @Override
    public Optional<Lembrete> buscarPorId(long id) {
        return jdbc.sql("SELECT * FROM lembrete WHERE id = ?").param(id).query(this::mapear).optional();
    }

    @Override
    public List<Lembrete> listar(Boolean ativo) {
        if (ativo == null) {
            return jdbc.sql("SELECT * FROM lembrete ORDER BY proxima_ocorrencia IS NULL, proxima_ocorrencia, id")
                    .query(this::mapear).list();
        }
        return jdbc.sql("SELECT * FROM lembrete WHERE ativo = ? ORDER BY proxima_ocorrencia IS NULL, proxima_ocorrencia, id")
                .param(ativo ? 1 : 0).query(this::mapear).list();
    }

    @Override
    public List<Lembrete> buscarComDisparoAte(Instant limite) {
        return jdbc.sql("SELECT * FROM lembrete WHERE ativo = 1 AND proximo_disparo IS NOT NULL "
                        + "AND proximo_disparo <= ? ORDER BY proximo_disparo")
                .param(limite.toEpochMilli()).query(this::mapear).list();
    }

    @Override
    public boolean remover(long id) {
        return jdbc.sql("DELETE FROM lembrete WHERE id = ?").param(id).update() > 0;
    }

    // ---- mapeamento ----

    private static Long millis(Instant i) {
        return i == null ? null : i.toEpochMilli();
    }

    private String participantesJson(Lembrete l) {
        try {
            return json.writeValueAsString(l.participantes());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** [tipo, dataHora, hora, diaSemana, diaMes] */
    private static Object[] colunasRecorrencia(Recorrencia r) {
        if (r instanceof Recorrencia.Unica u) return new Object[]{"UNICA", u.dataHora().toString(), null, null, null};
        if (r instanceof Recorrencia.Diaria d) return new Object[]{"DIARIA", null, d.hora().toString(), null, null};
        if (r instanceof Recorrencia.Semanal s)
            return new Object[]{"SEMANAL", null, s.hora().toString(), s.diaSemana().name(), null};
        Recorrencia.Mensal m = (Recorrencia.Mensal) r;
        return new Object[]{"MENSAL", null, m.hora().toString(), null, m.diaMes()};
    }

    private Recorrencia recorrencia(ResultSet rs) throws SQLException {
        return switch (rs.getString("rec_tipo")) {
            case "UNICA" -> new Recorrencia.Unica(LocalDateTime.parse(rs.getString("rec_data_hora")));
            case "DIARIA" -> new Recorrencia.Diaria(LocalTime.parse(rs.getString("rec_hora")));
            case "SEMANAL" -> new Recorrencia.Semanal(DayOfWeek.valueOf(rs.getString("rec_dia_semana")),
                    LocalTime.parse(rs.getString("rec_hora")));
            case "MENSAL" -> new Recorrencia.Mensal(rs.getInt("rec_dia_mes"), LocalTime.parse(rs.getString("rec_hora")));
            default -> throw new SQLException("rec_tipo desconhecido");
        };
    }

    private Lembrete mapear(ResultSet rs, int linha) throws SQLException {
        long ocorrencia = rs.getLong("proxima_ocorrencia");
        boolean semOcorrencia = rs.wasNull();
        try {
            return new Lembrete(rs.getLong("id"), rs.getString("titulo"), rs.getString("descricao"), rs.getString("mensagem"), recorrencia(rs),
                    rs.getInt("antecedencia_min"), rs.getString("contexto_md"),
                    json.readValue(rs.getString("participantes_json"), PARTICIPANTES),
                    semOcorrencia ? null : Instant.ofEpochMilli(ocorrencia), rs.getInt("ativo") == 1,
                    Instant.ofEpochMilli(rs.getLong("criado_em")));
        } catch (JsonProcessingException e) {
            throw new SQLException("participantes_json invalido", e);
        }
    }
}
