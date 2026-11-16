package dev.api.tarefas.core.application;

import dev.api.shared.core.DadosInvalidosException;
import dev.api.shared.core.RecursoNaoEncontradoException;
import dev.api.shared.core.port.RelogioPort;
import dev.api.tarefas.core.application.Dados.FiltroTarefas;
import dev.api.tarefas.core.application.Dados.Movimento;
import dev.api.tarefas.core.application.Dados.TarefaDados;
import dev.api.tarefas.core.application.Dados.TarefaVisao;
import dev.api.tarefas.core.application.port.ProjetoRepository;
import dev.api.tarefas.core.application.port.SquadRepository;
import dev.api.tarefas.core.application.port.TarefaRepository;
import dev.api.tarefas.core.domain.Projeto;
import dev.api.tarefas.core.domain.Squad;
import dev.api.tarefas.core.domain.StatusTarefa;
import dev.api.tarefas.core.domain.Tarefa;
import dev.api.tarefas.core.domain.Textos;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Casos de uso de tarefas: CRUD, listagem filtrada e mover no board. */
public class TarefaService {
    /** Distancia entre posicoes ao renumerar uma coluna. */
    static final double PASSO = 1024;

    private final SquadRepository squads;
    private final ProjetoRepository projetos;
    private final TarefaRepository tarefas;
    private final RelogioPort relogio;

    public TarefaService(SquadRepository squads, ProjetoRepository projetos, TarefaRepository tarefas, RelogioPort relogio) {
        this.squads = squads;
        this.projetos = projetos;
        this.tarefas = tarefas;
        this.relogio = relogio;
    }

    // ------------------------------------------------------------- consultas

    public List<TarefaVisao> listar(FiltroTarefas f) {
        var filtroBanco = new TarefaRepository.Filtro(f.squadId(), f.projetoId(),
                f.status() == null ? null : new HashSet<>(f.status()), f.prioridade(), f.concluidasDesde());
        String tag = f.tag() == null || f.tag().isBlank() ? null : f.tag().strip().toLowerCase(java.util.Locale.ROOT);
        List<String> termos = termos(f.busca());
        Map<Long, Squad> porSquad = squads.listar(true).stream().collect(Collectors.toMap(Squad::id, Function.identity()));
        Map<Long, Projeto> porProjeto = projetos.listar(null, true).stream()
                .collect(Collectors.toMap(Projeto::id, Function.identity()));
        return tarefas.listar(filtroBanco).stream()
                .filter(t -> tag == null || t.tags().contains(tag))
                .map(t -> visao(t, porSquad, porProjeto))
                .filter(v -> corresponde(v, termos))
                .toList();
    }

    public TarefaVisao obter(long id) {
        return visao(existente(id));
    }

    // ------------------------------------------------------------- escrita

    public TarefaVisao criar(TarefaDados d) {
        validarReferencias(d.squadId(), d.projetoId(), true);
        Instant agora = relogio.agora();
        StatusTarefa status = d.status() == null ? StatusTarefa.A_FAZER : d.status();
        double ordem = tarefas.maiorOrdem(d.squadId(), status) + PASSO;
        Tarefa nova = montar(() -> new Tarefa(null, d.squadId(), d.projetoId(), d.titulo(), d.descricao(), status,
                d.motivoBloqueio(), d.prioridade(), d.tags(), d.link(), ordem, agora, agora,
                status == StatusTarefa.FEITO ? agora : null));
        return visao(tarefas.salvar(nova));
    }

    /** Substitui os campos editaveis. Mudou de squad ou de status: vai para o fim da nova coluna. */
    public TarefaVisao atualizar(long id, TarefaDados d) {
        Tarefa atual = existente(id);
        boolean mudouSquad = !Objects.equals(d.squadId(), atual.squadId());
        boolean mudouProjeto = !Objects.equals(d.projetoId(), atual.projetoId());
        validarReferencias(d.squadId(), d.projetoId(), mudouSquad || mudouProjeto);
        Instant agora = relogio.agora();
        StatusTarefa status = d.status() == null ? atual.status() : d.status();
        boolean mudouColuna = mudouSquad || status != atual.status();
        double ordem = mudouColuna ? tarefas.maiorOrdem(d.squadId(), status) + PASSO : atual.ordem();
        Instant concluida = status == StatusTarefa.FEITO
                ? (atual.status() == StatusTarefa.FEITO ? atual.concluidaEm() : agora) : null;
        Tarefa nova = montar(() -> new Tarefa(id, d.squadId(), d.projetoId(), d.titulo(), d.descricao(), status,
                d.motivoBloqueio(), d.prioridade(), d.tags(), d.link(), ordem, atual.criadaEm(), agora, concluida));
        return visao(tarefas.salvar(nova));
    }

    /**
     * Move para a coluna {@code m.status()}, imediatamente antes de {@code m.antesDeId()} (ou no fim).
     * Renumera a coluna de destino (PASSO, 2*PASSO, ...) para a ordem nunca perder precisao.
     */
    public TarefaVisao mover(long id, Movimento m) {
        if (m.status() == null) throw new DadosInvalidosException("status e obrigatorio");
        Tarefa atual = existente(id);
        if (Objects.equals(m.antesDeId(), id)) throw new DadosInvalidosException("antesDeId nao pode ser a propria tarefa");

        List<Tarefa> coluna = new ArrayList<>(tarefas.listarColuna(atual.squadId(), m.status()));
        coluna.removeIf(t -> t.id().equals(id));
        int posicao = coluna.size();
        if (m.antesDeId() != null) {
            posicao = -1;
            for (int i = 0; i < coluna.size(); i++) {
                if (coluna.get(i).id().equals(m.antesDeId())) posicao = i;
            }
            if (posicao < 0) throw new DadosInvalidosException("antesDeId " + m.antesDeId() + " nao esta na coluna " + m.status());
        }

        String motivo = m.motivoBloqueio();
        if (motivo == null && m.status() == StatusTarefa.BLOQUEADO && atual.status() == StatusTarefa.BLOQUEADO)
            motivo = atual.motivoBloqueio();
        String motivoFinal = motivo;
        Tarefa movida = montar(() -> atual.comStatus(m.status(), motivoFinal, 0, relogio.agora()));

        coluna.add(posicao, movida);
        Map<Long, Double> novasOrdens = new HashMap<>();
        Tarefa salvar = movida;
        for (int i = 0; i < coluna.size(); i++) {
            double ordem = (i + 1) * PASSO;
            Tarefa t = coluna.get(i);
            if (t.id().equals(id)) salvar = t.comOrdem(ordem);
            else if (t.ordem() != ordem) novasOrdens.put(t.id(), ordem);
        }
        tarefas.salvar(salvar);
        if (!novasOrdens.isEmpty()) tarefas.atualizarOrdens(novasOrdens);
        return obter(id);
    }

    public void remover(long id) {
        existente(id);
        tarefas.remover(id);
    }

    // ------------------------------------------------------------- apoio

    private Tarefa existente(long id) {
        return tarefas.buscarPorId(id).orElseThrow(() -> new RecursoNaoEncontradoException("Tarefa " + id + " nao encontrada"));
    }

    /** Squad existe; projeto (se houver) existe e e do mesmo squad. Com {@code exigirAtivos}, nenhum pode estar arquivado. */
    private void validarReferencias(Long squadId, Long projetoId, boolean exigirAtivos) {
        if (squadId == null) throw new DadosInvalidosException("squadId e obrigatorio");
        Squad squad = squads.buscarPorId(squadId)
                .orElseThrow(() -> new DadosInvalidosException("Squad " + squadId + " nao existe"));
        if (exigirAtivos && squad.arquivado()) throw new DadosInvalidosException("Squad '" + squad.slug() + "' esta arquivado");
        if (projetoId == null) return;
        Projeto projeto = projetos.buscarPorId(projetoId)
                .orElseThrow(() -> new DadosInvalidosException("Projeto " + projetoId + " nao existe"));
        if (projeto.squadId() != squadId)
            throw new DadosInvalidosException("Projeto '" + projeto.slug() + "' nao pertence ao squad '" + squad.slug() + "'");
        if (exigirAtivos && projeto.arquivado())
            throw new DadosInvalidosException("Projeto '" + projeto.slug() + "' esta arquivado");
    }

    private TarefaVisao visao(Tarefa t) {
        Squad s = squads.buscarPorId(t.squadId()).orElse(null);
        Projeto p = t.projetoId() == null ? null : projetos.buscarPorId(t.projetoId()).orElse(null);
        return new TarefaVisao(t, s == null ? null : s.slug(), p == null ? null : p.slug(), p == null ? null : p.nome());
    }

    private static TarefaVisao visao(Tarefa t, Map<Long, Squad> squads, Map<Long, Projeto> projetos) {
        Squad s = squads.get(t.squadId());
        Projeto p = t.projetoId() == null ? null : projetos.get(t.projetoId());
        return new TarefaVisao(t, s == null ? null : s.slug(), p == null ? null : p.slug(), p == null ? null : p.nome());
    }

    private static List<String> termos(String busca) {
        String b = Textos.normalizar(busca).trim();
        return b.isEmpty() ? List.of() : List.of(b.split("\\s+"));
    }

    private static boolean corresponde(TarefaVisao v, List<String> termos) {
        if (termos.isEmpty()) return true;
        Tarefa t = v.tarefa();
        String texto = Textos.normalizar(String.join(" ", t.titulo(), Objects.toString(t.descricao(), ""),
                String.join(" ", t.tags()), Objects.toString(t.motivoBloqueio(), ""),
                Objects.toString(v.projetoSlug(), ""), Objects.toString(v.projetoNome(), "")));
        return termos.stream().allMatch(texto::contains);
    }

    /** Erros de validacao do dominio viram 400. */
    private static Tarefa montar(java.util.function.Supplier<Tarefa> construtor) {
        try {
            return construtor.get();
        } catch (IllegalArgumentException e) {
            throw new DadosInvalidosException(e.getMessage());
        }
    }
}
