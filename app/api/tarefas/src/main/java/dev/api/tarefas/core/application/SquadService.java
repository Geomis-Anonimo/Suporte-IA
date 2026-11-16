package dev.api.tarefas.core.application;

import dev.api.shared.core.ConflitoException;
import dev.api.shared.core.DadosInvalidosException;
import dev.api.shared.core.RecursoNaoEncontradoException;
import dev.api.tarefas.core.application.Dados.SquadDados;
import dev.api.tarefas.core.application.Dados.SquadVisao;
import dev.api.tarefas.core.application.port.ProjetoRepository;
import dev.api.tarefas.core.application.port.SquadRepository;
import dev.api.tarefas.core.application.port.TarefaRepository;
import dev.api.tarefas.core.domain.Squad;
import dev.api.tarefas.core.domain.StatusTarefa;
import java.util.List;
import java.util.Map;

/** Casos de uso de squads. */
public class SquadService {
    private final SquadRepository squads;
    private final ProjetoRepository projetos;
    private final TarefaRepository tarefas;

    public SquadService(SquadRepository squads, ProjetoRepository projetos, TarefaRepository tarefas) {
        this.squads = squads;
        this.projetos = projetos;
        this.tarefas = tarefas;
    }

    public List<SquadVisao> listar(boolean incluirArquivados) {
        Map<Long, Map<StatusTarefa, Long>> contagens = tarefas.contagemPorSquad();
        return squads.listar(incluirArquivados).stream()
                .map(s -> new SquadVisao(s, Dados.contagem(contagens.get(s.id()))))
                .toList();
    }

    public SquadVisao obter(long id) {
        Squad s = existente(id);
        return new SquadVisao(s, Dados.contagem(tarefas.contagemPorSquad().get(id)));
    }

    public SquadVisao criar(SquadDados d) {
        Squad novo = montar(null, d, d.ordem() == null ? squads.proximaOrdem() : d.ordem());
        exigirSlugLivre(novo.slug(), null);
        return obter(squads.salvar(novo).id());
    }

    public SquadVisao atualizar(long id, SquadDados d) {
        Squad atual = existente(id);
        Squad novo = montar(id, d, d.ordem() == null ? atual.ordem() : d.ordem());
        exigirSlugLivre(novo.slug(), id);
        squads.salvar(novo);
        return obter(id);
    }

    public void remover(long id) {
        existente(id);
        int nProjetos = projetos.contarPorSquad(id);
        int nTarefas = tarefas.contarPorSquad(id);
        if (nProjetos > 0 || nTarefas > 0)
            throw new ConflitoException("Squad tem " + nProjetos + " projeto(s) e " + nTarefas
                    + " tarefa(s); arquive ou mova antes de excluir");
        squads.remover(id);
    }

    Squad existente(long id) {
        return squads.buscarPorId(id).orElseThrow(() -> new RecursoNaoEncontradoException("Squad " + id + " nao encontrado"));
    }

    private void exigirSlugLivre(String slug, Long idAtual) {
        squads.buscarPorSlug(slug).filter(s -> !s.id().equals(idAtual)).ifPresent(s -> {
            throw new ConflitoException("Ja existe um squad com slug '" + slug + "'");
        });
    }

    private static Squad montar(Long id, SquadDados d, int ordem) {
        try {
            return new Squad(id, d.slug(), d.nome(), ordem, d.arquivado());
        } catch (IllegalArgumentException e) {
            throw new DadosInvalidosException(e.getMessage());
        }
    }
}
