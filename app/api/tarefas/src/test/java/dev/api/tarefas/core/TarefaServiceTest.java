package dev.api.tarefas.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.api.shared.core.ConflitoException;
import dev.api.shared.core.DadosInvalidosException;
import dev.api.shared.core.RecursoNaoEncontradoException;
import dev.api.shared.core.port.RelogioPort;
import dev.api.tarefas.core.application.Dados.FiltroTarefas;
import dev.api.tarefas.core.application.Dados.Movimento;
import dev.api.tarefas.core.application.Dados.ProjetoDados;
import dev.api.tarefas.core.application.Dados.SquadDados;
import dev.api.tarefas.core.application.Dados.TarefaDados;
import dev.api.tarefas.core.application.Dados.TarefaVisao;
import dev.api.tarefas.core.application.ProjetoService;
import dev.api.tarefas.core.application.SquadService;
import dev.api.tarefas.core.application.TarefaService;
import dev.api.tarefas.core.domain.Prioridade;
import dev.api.tarefas.core.domain.StatusTarefa;
import dev.api.tarefas.infra.persistence.JdbcProjetoRepository;
import dev.api.tarefas.infra.persistence.JdbcSquadRepository;
import dev.api.tarefas.infra.persistence.JdbcTarefaRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Servicos + repositorios JDBC num SQLite temporario (sem subir o Spring). */
class TarefaServiceTest {
    @TempDir
    Path dir;

    private Instant agora = Instant.parse("2026-11-16T12:00:00Z");
    private SquadService squads;
    private ProjetoService projetos;
    private TarefaService tarefas;
    private long guardian;
    private long telaWeb;
    private long whatsapp;

    @BeforeEach
    void preparar() {
        var ds = new SingleConnectionDataSource("jdbc:sqlite:" + dir.resolve("t.db"), true);
        new ResourceDatabasePopulator(new ClassPathResource("db/tarefas-schema.sql")).execute(ds);
        JdbcClient jdbc = JdbcClient.create(ds);
        var rs = new JdbcSquadRepository(jdbc);
        var rp = new JdbcProjetoRepository(jdbc);
        var rt = new JdbcTarefaRepository(jdbc, new ObjectMapper());
        RelogioPort relogio = new RelogioPort() {
            public Instant agora() { return agora; }
            public ZoneId fuso() { return ZoneId.of("America/Sao_Paulo"); }
        };
        squads = new SquadService(rs, rp, rt);
        projetos = new ProjetoService(rs, rp, rt);
        tarefas = new TarefaService(rs, rp, rt, relogio);
        guardian = squads.criar(new SquadDados("guardian", "Guardian", null, false)).squad().id();
        telaWeb = squads.criar(new SquadDados("tela-web", "Tela Web", null, false)).squad().id();
        whatsapp = projetos.criar(new ProjetoDados(guardian, "guardian_whatsapp", "guardian_whatsapp", null,
                "guardian/projects/guardian_whatsapp/README.md", false)).projeto().id();
    }

    private long nova(String titulo) {
        return nova(titulo, StatusTarefa.A_FAZER, whatsapp, null);
    }

    private long nova(String titulo, StatusTarefa status, Long projetoId, List<String> tags) {
        String motivo = status == StatusTarefa.BLOQUEADO ? "aguardando" : null;
        return tarefas.criar(new TarefaDados(guardian, projetoId, titulo, null, status, motivo, null, tags, null)).tarefa().id();
    }

    private List<String> coluna(StatusTarefa status) {
        return tarefas.listar(new FiltroTarefas(guardian, null, List.of(status), null, null, null, null)).stream()
                .map(v -> v.tarefa().titulo()).toList();
    }

    @Test
    void criaNoFimDaColuna() {
        nova("A");
        nova("B");
        assertThat(coluna(StatusTarefa.A_FAZER)).containsExactly("A", "B");
        TarefaVisao v = tarefas.obter(nova("C"));
        assertThat(v.squadSlug()).isEqualTo("guardian");
        assertThat(v.projetoSlug()).isEqualTo("guardian_whatsapp");
        assertThat(v.tarefa().ordem()).isEqualTo(3 * 1024);
    }

    @Test
    void moverParaOutraColunaAntesDeOutra() {
        long a = nova("A");
        long b = nova("B");
        long x = nova("X", StatusTarefa.FAZENDO, whatsapp, null);
        tarefas.mover(b, new Movimento(StatusTarefa.FAZENDO, x, null));
        assertThat(coluna(StatusTarefa.FAZENDO)).containsExactly("B", "X");
        assertThat(coluna(StatusTarefa.A_FAZER)).containsExactly("A");
        tarefas.mover(a, new Movimento(StatusTarefa.FAZENDO, null, null));
        assertThat(coluna(StatusTarefa.FAZENDO)).containsExactly("B", "X", "A");
    }

    @Test
    void reordenarNaMesmaColunaEColunaVazia() {
        long a = nova("A");
        nova("B");
        long c = nova("C");
        tarefas.mover(c, new Movimento(StatusTarefa.A_FAZER, a, null));
        assertThat(coluna(StatusTarefa.A_FAZER)).containsExactly("C", "A", "B");
        tarefas.mover(a, new Movimento(StatusTarefa.FEITO, null, null));
        assertThat(coluna(StatusTarefa.FEITO)).containsExactly("A");
        assertThat(tarefas.obter(a).tarefa().concluidaEm()).isEqualTo(agora);
    }

    @Test
    void moverValida() {
        long a = nova("A");
        long x = nova("X", StatusTarefa.FAZENDO, whatsapp, null);
        assertThatThrownBy(() -> tarefas.mover(a, new Movimento(StatusTarefa.BLOQUEADO, null, null)))
                .isInstanceOf(DadosInvalidosException.class).hasMessageContaining("motivoBloqueio");
        assertThatThrownBy(() -> tarefas.mover(a, new Movimento(StatusTarefa.A_FAZER, x, null)))
                .isInstanceOf(DadosInvalidosException.class).hasMessageContaining("nao esta na coluna");
        assertThatThrownBy(() -> tarefas.mover(999, new Movimento(StatusTarefa.A_FAZER, null, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        tarefas.mover(a, new Movimento(StatusTarefa.BLOQUEADO, null, "acesso do DevOps"));
        assertThat(tarefas.obter(a).tarefa().motivoBloqueio()).isEqualTo("acesso do DevOps");
        tarefas.mover(a, new Movimento(StatusTarefa.FAZENDO, null, null));
        assertThat(tarefas.obter(a).tarefa().motivoBloqueio()).isNull();
    }

    @Test
    void atualizarStatusPelaEdicao() {
        long a = nova("A");
        agora = agora.plusSeconds(3600);
        var feita = tarefas.atualizar(a, new TarefaDados(guardian, whatsapp, "A editada", "desc", StatusTarefa.FEITO, null,
                Prioridade.ALTA, List.of("gmud"), "https://x.y/z")).tarefa();
        assertThat(feita.concluidaEm()).isEqualTo(agora);
        assertThat(feita.criadaEm()).isEqualTo(agora.minusSeconds(3600));
        assertThat(feita.prioridade()).isEqualTo(Prioridade.ALTA);
        var reaberta = tarefas.atualizar(a, new TarefaDados(guardian, whatsapp, "A editada", null, StatusTarefa.A_FAZER, null,
                null, null, null)).tarefa();
        assertThat(reaberta.concluidaEm()).isNull();
    }

    @Test
    void referenciasValidas() {
        long outroProjeto = projetos.criar(new ProjetoDados(telaWeb, "bca_whatsapp", "bca_whatsapp", null, null, false))
                .projeto().id();
        assertThatThrownBy(() -> tarefas.criar(new TarefaDados(guardian, outroProjeto, "X", null, null, null, null, null, null)))
                .isInstanceOf(DadosInvalidosException.class).hasMessageContaining("nao pertence");
        assertThatThrownBy(() -> tarefas.criar(new TarefaDados(999L, null, "X", null, null, null, null, null, null)))
                .isInstanceOf(DadosInvalidosException.class);
        squads.atualizar(telaWeb, new SquadDados("tela-web", "Tela Web", null, true));
        assertThatThrownBy(() -> tarefas.criar(new TarefaDados(telaWeb, null, "X", null, null, null, null, null, null)))
                .hasMessageContaining("arquivado");
    }

    @Test
    void filtros() {
        nova("Validar template Omne", StatusTarefa.A_FAZER, whatsapp, List.of("omne", "gmud"));
        nova("Pedir acesso à VPN", StatusTarefa.FAZENDO, null, null);
        long antiga = nova("Concluída há tempo", StatusTarefa.A_FAZER, whatsapp, null);
        tarefas.mover(antiga, new Movimento(StatusTarefa.FEITO, null, null));
        agora = agora.plusSeconds(30L * 24 * 3600);
        nova("Concluída hoje", StatusTarefa.FEITO, whatsapp, null);

        assertThat(titulos(new FiltroTarefas(guardian, 0L, null, null, null, null, null))).containsExactly("Pedir acesso à VPN");
        assertThat(titulos(new FiltroTarefas(guardian, null, List.of(StatusTarefa.A_FAZER, StatusTarefa.FAZENDO), null, null, null, null)))
                .containsExactly("Validar template Omne", "Pedir acesso à VPN");
        assertThat(titulos(new FiltroTarefas(null, null, null, null, "GMUD", null, null))).containsExactly("Validar template Omne");
        assertThat(titulos(new FiltroTarefas(null, null, null, null, null, "acesso a vpn", null))).containsExactly("Pedir acesso à VPN");
        assertThat(titulos(new FiltroTarefas(null, null, null, null, null, "guardian_whatsapp omne", null)))
                .containsExactly("Validar template Omne");
        assertThat(titulos(new FiltroTarefas(guardian, null, List.of(StatusTarefa.FEITO), null, null, null,
                agora.minusSeconds(14L * 24 * 3600)))).containsExactly("Concluída hoje");
    }

    private List<String> titulos(FiltroTarefas f) {
        return tarefas.listar(f).stream().map(v -> v.tarefa().titulo()).toList();
    }

    @Test
    void conflitosEContagem() {
        nova("A");
        nova("B", StatusTarefa.BLOQUEADO, whatsapp, null);
        assertThatThrownBy(() -> squads.criar(new SquadDados("guardian", "Outro", null, false)))
                .isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> projetos.criar(new ProjetoDados(guardian, "guardian_whatsapp", "x", null, null, false)))
                .isInstanceOf(ConflitoException.class);
        assertThatThrownBy(() -> squads.remover(guardian)).isInstanceOf(ConflitoException.class).hasMessageContaining("tarefa");
        assertThatThrownBy(() -> projetos.remover(whatsapp)).isInstanceOf(ConflitoException.class);
        squads.remover(telaWeb);

        var visao = squads.obter(guardian);
        assertThat(visao.contagem()).containsEntry(StatusTarefa.A_FAZER, 1L).containsEntry(StatusTarefa.BLOQUEADO, 1L)
                .containsEntry(StatusTarefa.FEITO, 0L);
        assertThat(projetos.listar(guardian, false).get(0).contagem()).containsEntry(StatusTarefa.A_FAZER, 1L);
    }
}
