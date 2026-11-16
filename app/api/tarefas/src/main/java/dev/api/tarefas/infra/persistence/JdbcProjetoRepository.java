package dev.api.tarefas.infra.persistence;

import dev.api.tarefas.core.application.port.ProjetoRepository;
import dev.api.tarefas.core.domain.Projeto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/** Adaptador SQLite da porta {@link ProjetoRepository}. */
public class JdbcProjetoRepository implements ProjetoRepository {
    private final JdbcClient jdbc;

    public JdbcProjetoRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Projeto salvar(Projeto p) {
        if (p.id() == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.sql("INSERT INTO projeto (squad_id, slug, nome, descricao, contexto_md, arquivado) VALUES (?,?,?,?,?,?)")
                    .params(p.squadId(), p.slug(), p.nome(), p.descricao(), p.contextoMd(), p.arquivado() ? 1 : 0)
                    .update(keys);
            return p.comId(keys.getKey().longValue());
        }
        jdbc.sql("UPDATE projeto SET squad_id=?, slug=?, nome=?, descricao=?, contexto_md=?, arquivado=? WHERE id=?")
                .params(p.squadId(), p.slug(), p.nome(), p.descricao(), p.contextoMd(), p.arquivado() ? 1 : 0, p.id())
                .update();
        return p;
    }

    @Override
    public Optional<Projeto> buscarPorId(long id) {
        return jdbc.sql("SELECT * FROM projeto WHERE id = ?").param(id).query(JdbcProjetoRepository::mapear).optional();
    }

    @Override
    public Optional<Projeto> buscarPorSlug(long squadId, String slug) {
        return jdbc.sql("SELECT * FROM projeto WHERE squad_id = ? AND slug = ?").params(squadId, slug)
                .query(JdbcProjetoRepository::mapear).optional();
    }

    @Override
    public List<Projeto> listar(Long squadId, boolean incluirArquivados) {
        List<String> condicoes = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (squadId != null) {
            condicoes.add("squad_id = ?");
            params.add(squadId);
        }
        if (!incluirArquivados) condicoes.add("arquivado = 0");
        String where = condicoes.isEmpty() ? "" : " WHERE " + String.join(" AND ", condicoes);
        return jdbc.sql("SELECT * FROM projeto" + where + " ORDER BY nome COLLATE NOCASE").params(params)
                .query(JdbcProjetoRepository::mapear).list();
    }

    @Override
    public int contarPorSquad(long squadId) {
        return jdbc.sql("SELECT COUNT(*) FROM projeto WHERE squad_id = ?").param(squadId).query(Integer.class).single();
    }

    @Override
    public boolean remover(long id) {
        return jdbc.sql("DELETE FROM projeto WHERE id = ?").param(id).update() > 0;
    }

    private static Projeto mapear(ResultSet rs, int linha) throws SQLException {
        return new Projeto(rs.getLong("id"), rs.getLong("squad_id"), rs.getString("slug"), rs.getString("nome"),
                rs.getString("descricao"), rs.getString("contexto_md"), rs.getInt("arquivado") == 1);
    }
}
