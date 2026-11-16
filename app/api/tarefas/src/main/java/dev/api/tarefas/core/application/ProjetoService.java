package dev.api.tarefas.core.application;

import dev.api.shared.core.ConflitoException;
import dev.api.shared.core.DadosInvalidosException;
import dev.api.shared.core.RecursoNaoEncontradoException;
import dev.api.tarefas.core.application.Dados.ProjetoDados;
import dev.api.tarefas.core.application.Dados.ProjetoVisao;
import dev.api.tarefas.core.application.port.ProjetoRepository;
import dev.api.tarefas.core.application.port.SquadRepository;
import dev.api.tarefas.core.application.port.TarefaRepository;
import dev.api.tarefas.core.domain.Projeto;
import dev.api.tarefas.core.domain.Squad;
import dev.api.tarefas.core.domain.StatusTarefa;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Casos de uso de projetos. */
public class ProjetoService {
    private final SquadRepository squads;
    private final ProjetoRepository projetos;
    private final TarefaRepository tarefas;

    public ProjetoService(SquadRepository squads, ProjetoRepository projetos, TarefaRepository tarefas) {
        this.squads = squads;
        this.projetos = projetos;
        this.tarefas = tarefas;
    }

    public List<ProjetoVisao> listar(Long squadId, boolean incluirArquivados) {
        Map<Long, String> slugs = squads.listar(true).stream().collect(Collectors.toMap(Squad::id, Squad::slug));
        Map<Long, Map<StatusTarefa, Long>> contagens = tarefas.contagemPorProjeto();
        return projetos.listar(squadId, incluirArquivados).stream()
                .map(p -> new ProjetoVisao(p, slugs.get(p.squadId()), Dados.contagem(contagens.get(p.id()))))
                .toList();
    }

    public ProjetoVisao obter(long id) {
        Projeto p = existente(id);
        String slug = squads.buscarPorId(p.squadId()).map(Squad::slug).orElse(null);
        return new ProjetoVisao(p, slug, Dados.contagem(tarefas.contagemPorProjeto().get(id)));
    }

    public ProjetoVisao criar(ProjetoDados d) {
        Squad squad = squadValido(d.squadId());
        if (squad.arquivado()) throw new DadosInvalidosException("Squad '" + squad.slug() + "' esta arquivado");
        Projeto novo = montar(null, d);
        exigirSlugLivre(novo, null);
        return obter(projetos.salvar(novo).id());
    }

    public ProjetoVisao atualizar(long id, ProjetoDados d) {
        Projeto atual = existente(id);
        squadValido(d.squadId());
        Projeto novo = montar(id, d);
        if (novo.squadId() != atual.squadId() && tarefas.contarPorProjeto(id) > 0)
            throw new ConflitoException("Projeto tem tarefas; nao pode trocar de squad");
        exigirSlugLivre(novo, id);
        projetos.salvar(novo);
        return obter(id);
    }

    public void remover(long id) {
        existente(id);
        int n = tarefas.contarPorProjeto(id);
        if (n > 0) throw new ConflitoException("Projeto tem " + n + " tarefa(s); arquive ou mova antes de excluir");
        projetos.remover(id);
    }

    Projeto existente(long id) {
        return projetos.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Projeto " + id + " nao encontrado"));
    }

    private Squad squadValido(Long squadId) {
        if (squadId == null) throw new DadosInvalidosException("squadId e obrigatorio");
        return squads.buscarPorId(squadId)
                .orElseThrow(() -> new DadosInvalidosException("Squad " + squadId + " nao existe"));
    }

    private void exigirSlugLivre(Projeto p, Long idAtual) {
        projetos.buscarPorSlug(p.squadId(), p.slug()).filter(x -> !x.id().equals(idAtual)).ifPresent(x -> {
            throw new ConflitoException("Ja existe um projeto '" + p.slug() + "' neste squad");
        });
    }

    private static Projeto montar(Long id, ProjetoDados d) {
        try {
            return new Projeto(id, d.squadId(), d.slug(), d.nome(), d.descricao(), d.contextoMd(), d.arquivado());
        } catch (IllegalArgumentException e) {
            throw new DadosInvalidosException(e.getMessage());
        }
    }
}
