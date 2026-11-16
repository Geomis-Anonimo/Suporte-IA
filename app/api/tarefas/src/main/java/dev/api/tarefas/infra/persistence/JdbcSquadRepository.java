package dev.api.tarefas.infra.persistence;

import dev.api.tarefas.core.application.port.SquadRepository;
import dev.api.tarefas.core.domain.Squad;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/** Adaptador SQLite da porta {@link SquadRepository}. */
public class JdbcSquadRepository implements SquadRepository {
    private final JdbcClient jdbc;

    public JdbcSquadRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Squad salvar(Squad s) {
        if (s.id() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.sql("INSERT INTO squad (slug, nome, ordem, arquivado) VALUES (?,?,?,?)")
                    .params(s.slug(), s.nome(), s.ordem(), s.arquivado() ? 1 : 0).update(keys);
            return s.comId(keys.getKey().longValue());
        }
        jdbc.sql("UPDATE squad SET slug=?, nome=?, ordem=?, arquivado=? WHERE id=?")
                .params(s.slug(), s.nome(), s.ordem(), s.arquivado() ? 1 : 0, s.id()).update();
        return s;
    }

    @Override
    public Optional<Squad> buscarPorId(long id) {
        return jdbc.sql("SELECT * FROM squad WHERE id = ?").param(id).query(JdbcSquadRepository::mapear).optional();
    }

    @Override
    public Optional<Squad> buscarPorSlug(String slug) {
        return jdbc.sql("SELECT * FROM squad WHERE slug = ?").param(slug).query(JdbcSquadRepository::mapear).optional();
    }

    @Override
    public List<Squad> listar(boolean incluirArquivados) {
        String where = incluirArquivados ? "" : " WHERE arquivado = 0";
        return jdbc.sql("SELECT * FROM squad" + where + " ORDER BY ordem, nome").query(JdbcSquadRepository::mapear).list();
    }

    @Override
    public int proximaOrdem() {
        Integer max = jdbc.sql("SELECT MAX(ordem) FROM squad").query(Integer.class).single();
        return max == null ? 1 : max + 1;
    }

    @Override
    public boolean remover(long id) {
        return jdbc.sql("DELETE FROM squad WHERE id = ?").param(id).update() > 0;
    }

    private static Squad mapear(ResultSet rs, int linha) throws SQLException {
        return new Squad(rs.getLong("id"), rs.getString("slug"), rs.getString("nome"), rs.getInt("ordem"),
                rs.getInt("arquivado") == 1);
    }
}
