package dev.api.tarefas.infra.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.api.tarefas.core.application.port.TarefaRepository;
import dev.api.tarefas.core.domain.Prioridade;
import dev.api.tarefas.core.domain.StatusTarefa;
import dev.api.tarefas.core.domain.Tarefa;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/** Adaptador SQLite da porta {@link TarefaRepository}. Instantes em epoch millis; tags em JSON. */
public class JdbcTarefaRepository implements TarefaRepository {
    private static final TypeReference<List<String>> TAGS = new TypeReference<>() { };
    /** Ordena as colunas na ordem do board (A_FAZER, FAZENDO, BLOQUEADO, FEITO). */
    private static final String ORDEM_STATUS = "CASE status WHEN 'A_FAZER' THEN 0 WHEN 'FAZENDO' THEN 1 "
            + "WHEN 'BLOQUEADO' THEN 2 ELSE 3 END";

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JdbcTarefaRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Tarefa salvar(Tarefa t) {
        Object[] valores = {t.squadId(), t.projetoId(), t.titulo(), t.descricao(), t.status().name(), t.motivoBloqueio(),
                t.prioridade().name(), tagsJson(t.tags()), t.link(), t.ordem(), millis(t.criadaEm()),
                millis(t.atualizadaEm()), millis(t.concluidaEm())};
        if (t.id() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.sql("""
                    INSERT INTO tarefa (squad_id, projeto_id, titulo, descricao, status, motivo_bloqueio, prioridade,
                      tags_json, link, ordem, criada_em, atualizada_em, concluida_em)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""").params(valores).update(keys);
            return t.comId(keys.getKey().longValue());
        }
        jdbc.sql("""
                UPDATE tarefa SET squad_id=?, projeto_id=?, titulo=?, descricao=?, status=?, motivo_bloqueio=?,
                  prioridade=?, tags_json=?, link=?, ordem=?, criada_em=?, atualizada_em=?, concluida_em=?
                WHERE id=?""").params(comId(valores, t.id())).update();
        return t;
    }

    @Override
    public Optional<Tarefa> buscarPorId(long id) {
        return jdbc.sql("SELECT * FROM tarefa WHERE id = ?").param(id).query(this::mapear).optional();
    }

    @Override
    public List<Tarefa> listar(Filtro f) {
        List<String> cond = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (f.squadId() != null) {
            cond.add("squad_id = ?");
            params.add(f.squadId());
        }
        if (f.projetoId() != null) {
            if (f.projetoId() == 0) {
                cond.add("projeto_id IS NULL");
            } else {
                cond.add("projeto_id = ?");
                params.add(f.projetoId());
            }
        }
        if (!f.status().isEmpty()) {
            cond.add("status IN (" + f.status().stream().map(s -> "?").collect(Collectors.joining(",")) + ")");
            f.status().forEach(s -> params.add(s.name()));
        }
        if (f.prioridade() != null) {
            cond.add("prioridade = ?");
            params.add(f.prioridade().name());
        }
        if (f.concluidasDesde() != null) {
            cond.add("(status <> 'FEITO' OR concluida_em >= ?)");
            params.add(f.concluidasDesde().toEpochMilli());
        }
        String where = cond.isEmpty() ? "" : " WHERE " + String.join(" AND ", cond);
        return jdbc.sql("SELECT * FROM tarefa" + where + " ORDER BY " + ORDEM_STATUS + ", ordem, id")
                .params(params).query(this::mapear).list();
    }

    @Override
    public List<Tarefa> listarColuna(long squadId, StatusTarefa status) {
        return jdbc.sql("SELECT * FROM tarefa WHERE squad_id = ? AND status = ? ORDER BY ordem, id")
                .params(squadId, status.name()).query(this::mapear).list();
    }

    @Override
    public double maiorOrdem(long squadId, StatusTarefa status) {
        Double max = jdbc.sql("SELECT MAX(ordem) FROM tarefa WHERE squad_id = ? AND status = ?")
                .params(squadId, status.name()).query(Double.class).single();
        return max == null ? 0 : max;
    }

    @Override
    public void atualizarOrdens(Map<Long, Double> ordemPorId) {
        ordemPorId.forEach((id, ordem) -> jdbc.sql("UPDATE tarefa SET ordem = ? WHERE id = ?").params(ordem, id).update());
    }

    @Override
    public int contarPorSquad(long squadId) {
        return jdbc.sql("SELECT COUNT(*) FROM tarefa WHERE squad_id = ?").param(squadId).query(Integer.class).single();
    }

    @Override
    public int contarPorProjeto(long projetoId) {
        return jdbc.sql("SELECT COUNT(*) FROM tarefa WHERE projeto_id = ?").param(projetoId).query(Integer.class).single();
    }

    @Override
    public Map<Long, Map<StatusTarefa, Long>> contagemPorSquad() {
        return contagem("squad_id");
    }

    @Override
    public Map<Long, Map<StatusTarefa, Long>> contagemPorProjeto() {
        return contagem("projeto_id");
    }

    @Override
    public boolean remover(long id) {
        return jdbc.sql("DELETE FROM tarefa WHERE id = ?").param(id).update() > 0;
    }

    // ---- apoio ----

    private Map<Long, Map<StatusTarefa, Long>> contagem(String coluna) {
        Map<Long, Map<StatusTarefa, Long>> r = new HashMap<>();
        jdbc.sql("SELECT " + coluna + " AS chave, status, COUNT(*) AS n FROM tarefa WHERE " + coluna
                        + " IS NOT NULL GROUP BY " + coluna + ", status")
                .query((rs, i) -> {
                    r.computeIfAbsent(rs.getLong("chave"), k -> new EnumMap<>(StatusTarefa.class))
                            .put(StatusTarefa.valueOf(rs.getString("status")), rs.getLong("n"));
                    return null;
                }).list();
        return r;
    }

    private static Object[] comId(Object[] valores, long id) {
        Object[] r = java.util.Arrays.copyOf(valores, valores.length + 1);
        r[valores.length] = id;
        return r;
    }

    private static Long millis(Instant i) {
        return i == null ? null : i.toEpochMilli();
    }

    private static Instant instante(ResultSet rs, String coluna) throws SQLException {
        long v = rs.getLong(coluna);
        return rs.wasNull() ? null : Instant.ofEpochMilli(v);
    }

    private String tagsJson(List<String> tags) {
        try {
            return json.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Tarefa mapear(ResultSet rs, int linha) throws SQLException {
        long projeto = rs.getLong("projeto_id");
        Long projetoId = rs.wasNull() ? null : projeto;
        try {
            return new Tarefa(rs.getLong("id"), rs.getLong("squad_id"), projetoId, rs.getString("titulo"),
                    rs.getString("descricao"), StatusTarefa.valueOf(rs.getString("status")), rs.getString("motivo_bloqueio"),
                    Prioridade.valueOf(rs.getString("prioridade")), json.readValue(rs.getString("tags_json"), TAGS),
                    rs.getString("link"), rs.getDouble("ordem"), instante(rs, "criada_em"), instante(rs, "atualizada_em"),
                    instante(rs, "concluida_em"));
        } catch (JsonProcessingException e) {
            throw new SQLException("tags_json invalido", e);
        }
    }
}
