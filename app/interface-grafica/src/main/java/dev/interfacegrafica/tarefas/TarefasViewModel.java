package dev.interfacegrafica.tarefas;

import dev.interfacegrafica.client.ApiOfflineException;
import dev.interfacegrafica.client.TarefasClient;
import dev.interfacegrafica.client.dto.TarefasDtos.Prioridade;
import dev.interfacegrafica.client.dto.TarefasDtos.ProjetoDto;
import dev.interfacegrafica.client.dto.TarefasDtos.ProjetoRequest;
import dev.interfacegrafica.client.dto.TarefasDtos.SquadDto;
import dev.interfacegrafica.client.dto.TarefasDtos.SquadRequest;
import dev.interfacegrafica.client.dto.TarefasDtos.Status;
import dev.interfacegrafica.client.dto.TarefasDtos.TarefaDto;
import dev.interfacegrafica.client.dto.TarefasDtos.TarefaRequest;
import dev.interfacegrafica.config.AppConfig;
import dev.interfacegrafica.ui.ApiMonitor;
import dev.interfacegrafica.ui.Async;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;

/** Estado da tela de tarefas e chamadas a api (sempre fora da FX thread). */
public class TarefasViewModel {

    /** O que esta selecionado na arvore. {@code projetoId}: null = squad inteiro; 0 = "Sem projeto". */
    public record Selecao(Long squadId, Long projetoId) {
        public static final Selecao NENHUMA = new Selecao(null, null);
    }

    public enum Estado { CARREGANDO, OK, OFFLINE, ERRO }

    /** A coluna Feito mostra so as concluidas nesse periodo (a menos que "ver todas"). */
    public static final int DIAS_FEITO = 14;

    private final TarefasClient client;
    private final ApiMonitor monitor;

    private final ObservableList<SquadDto> squads = FXCollections.observableArrayList();
    private final ObservableList<ProjetoDto> projetos = FXCollections.observableArrayList();
    private final ObservableList<TarefaDto> tarefas = FXCollections.observableArrayList();
    private final ObjectProperty<Selecao> selecao = new SimpleObjectProperty<>(Selecao.NENHUMA);
    private final ObjectProperty<Long> selecionadaId = new SimpleObjectProperty<>();
    private final StringProperty busca = new SimpleStringProperty("");
    private final ObservableSet<Prioridade> prioridades = FXCollections.observableSet();
    private final ObservableSet<String> tags = FXCollections.observableSet();
    private final BooleanProperty feitoTodas = new SimpleBooleanProperty(false);
    private final BooleanProperty mostrarArquivados = new SimpleBooleanProperty(false);
    private final ObjectProperty<Estado> estado = new SimpleObjectProperty<>(Estado.CARREGANDO);
    private final IntegerProperty pendentes = new SimpleIntegerProperty(0);
    private final BooleanBinding ocupado = pendentes.greaterThan(0);
    private final ObjectBinding<FiltroTarefas> filtro;
    private long geracaoTarefas;

    public TarefasViewModel(TarefasClient client, ApiMonitor monitor) {
        this.client = client;
        this.monitor = monitor;
        filtro = Bindings.createObjectBinding(
                () -> new FiltroTarefas(busca.get(), prioridades, tags, selecao.get().projetoId()),
                busca, prioridades, tags, selecao);
    }

    // ------------------------------------------------------------- propriedades

    public ObservableList<SquadDto> squads() { return squads; }
    public ObservableList<ProjetoDto> projetos() { return projetos; }
    public ObservableList<TarefaDto> tarefas() { return tarefas; }
    public ObjectProperty<Selecao> selecaoProperty() { return selecao; }
    public ObjectProperty<Long> selecionadaIdProperty() { return selecionadaId; }
    public StringProperty buscaProperty() { return busca; }
    public ObservableSet<Prioridade> prioridades() { return prioridades; }
    public ObservableSet<String> tags() { return tags; }
    public BooleanProperty feitoTodasProperty() { return feitoTodas; }
    public BooleanProperty mostrarArquivadosProperty() { return mostrarArquivados; }
    public ObjectProperty<Estado> estadoProperty() { return estado; }
    public BooleanBinding ocupadoProperty() { return ocupado; }
    public ObjectBinding<FiltroTarefas> filtroProperty() { return filtro; }

    public SquadDto squadSelecionado() {
        Long id = selecao.get().squadId();
        return id == null ? null : squads.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
    }

    public ProjetoDto projeto(Long id) {
        return id == null ? null : projetos.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }

    public TarefaDto tarefa(Long id) {
        return id == null ? null : tarefas.stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
    }

    /** Tarefas visiveis de uma coluna, ja filtradas e na ordem do board. */
    public List<TarefaDto> coluna(Status status) {
        FiltroTarefas f = filtro.get();
        return tarefas.stream().filter(t -> t.status() == status).filter(f).toList();
    }

    public void limparFiltros() {
        busca.set("");
        prioridades.clear();
        tags.clear();
    }

    // ------------------------------------------------------------- carga

    /**
     * Recarrega squads e projetos (com contagens) e depois as tarefas do squad selecionado.
     * Se nada estiver selecionado (ou a selecao sumiu), seleciona {@code preferida} ou o primeiro squad.
     */
    public void recarregar(Selecao preferida, Consumer<Throwable> aoFalhar) {
        boolean arquivados = mostrarArquivados.get();
        chamar(() -> new Object[]{client.listarSquads(arquivados), client.listarProjetos(null, arquivados)}, r -> {
            @SuppressWarnings("unchecked") List<SquadDto> s = (List<SquadDto>) r[0];
            @SuppressWarnings("unchecked") List<ProjetoDto> p = (List<ProjetoDto>) r[1];
            squads.setAll(s);
            projetos.setAll(p);
            selecao.set(ajustarSelecao(preferida == null ? selecao.get() : preferida));
            recarregarTarefas(aoFalhar);
        }, erro -> {
            if (erro instanceof ApiOfflineException) {
                squads.clear();
                projetos.clear();
                tarefas.clear();
                estado.set(Estado.OFFLINE);
            } else {
                estado.set(Estado.ERRO);
            }
            aoFalhar.accept(erro);
        });
    }

    public void recarregarTarefas(Consumer<Throwable> aoFalhar) {
        Long squadId = selecao.get().squadId();
        long minha = ++geracaoTarefas;
        if (squadId == null) {
            tarefas.clear();
            estado.set(Estado.OK);
            return;
        }
        OffsetDateTime desde = feitoTodas.get() ? null : OffsetDateTime.now(AppConfig.FUSO).minusDays(DIAS_FEITO);
        if (tarefas.isEmpty()) estado.set(Estado.CARREGANDO);
        chamar(() -> client.listarTarefas(squadId, desde), lista -> {
            if (minha != geracaoTarefas) return;
            tarefas.setAll(lista);
            if (selecionadaId.get() != null && tarefa(selecionadaId.get()) == null) selecionadaId.set(null);
            estado.set(Estado.OK);
        }, erro -> {
            if (minha != geracaoTarefas) return;
            estado.set(erro instanceof ApiOfflineException ? Estado.OFFLINE : Estado.ERRO);
            aoFalhar.accept(erro);
        });
    }

    private Selecao ajustarSelecao(Selecao s) {
        boolean squadExiste = s != null && s.squadId() != null && squads.stream().anyMatch(x -> x.id().equals(s.squadId()));
        if (!squadExiste) return squads.isEmpty() ? Selecao.NENHUMA : new Selecao(squads.get(0).id(), null);
        Long p = s.projetoId();
        if (p != null && p != FiltroTarefas.SEM_PROJETO
                && projetos.stream().noneMatch(x -> x.id().equals(p) && x.squadId() == s.squadId())) {
            return new Selecao(s.squadId(), null);
        }
        return s;
    }

    // ------------------------------------------------------------- tarefas

    /** POST (id nulo) ou PUT; depois recarrega tudo (contagens mudam). */
    public void salvarTarefa(Long id, TarefaRequest req, Consumer<TarefaDto> ok, Consumer<Throwable> falha) {
        chamar(() -> id == null ? client.criarTarefa(req) : client.atualizarTarefa(id, req), salva -> {
            selecionadaId.set(salva.id());
            ok.accept(salva);
            recarregar(null, t -> { });
        }, falha);
    }

    public void mover(long id, Status status, Long antesDeId, String motivo, Consumer<TarefaDto> ok, Consumer<Throwable> falha) {
        chamar(() -> client.moverTarefa(id, status, antesDeId, motivo), movida -> {
            selecionadaId.set(movida.id());
            ok.accept(movida);
            recarregar(null, t -> { });
        }, falha);
    }

    public void excluirTarefa(long id, Runnable ok, Consumer<Throwable> falha) {
        chamar(() -> {
            client.excluirTarefa(id);
            return Boolean.TRUE;
        }, r -> {
            if (Objects.equals(selecionadaId.get(), id)) selecionadaId.set(null);
            ok.run();
            recarregar(null, t -> { });
        }, falha);
    }

    // ------------------------------------------------------------- squads e projetos

    public void salvarSquad(Long id, SquadRequest req, Consumer<SquadDto> ok, Consumer<Throwable> falha) {
        chamar(() -> id == null ? client.criarSquad(req) : client.atualizarSquad(id, req), salvo -> {
            ok.accept(salvo);
            recarregar(id == null ? new Selecao(salvo.id(), null) : null, t -> { });
        }, falha);
    }

    public void excluirSquad(long id, Runnable ok, Consumer<Throwable> falha) {
        chamar(() -> {
            client.excluirSquad(id);
            return Boolean.TRUE;
        }, r -> {
            ok.run();
            recarregar(null, t -> { });
        }, falha);
    }

    public void salvarProjeto(Long id, ProjetoRequest req, Consumer<ProjetoDto> ok, Consumer<Throwable> falha) {
        chamar(() -> id == null ? client.criarProjeto(req) : client.atualizarProjeto(id, req), salvo -> {
            ok.accept(salvo);
            recarregar(id == null ? new Selecao(salvo.squadId(), salvo.id()) : null, t -> { });
        }, falha);
    }

    public void excluirProjeto(long id, Runnable ok, Consumer<Throwable> falha) {
        chamar(() -> {
            client.excluirProjeto(id);
            return Boolean.TRUE;
        }, r -> {
            ok.run();
            Selecao s = selecao.get();
            recarregar(Objects.equals(s.projetoId(), id) ? new Selecao(s.squadId(), null) : null, t -> { });
        }, falha);
    }

    private <T> void chamar(Callable<T> trabalho, Consumer<T> ok, Consumer<Throwable> falha) {
        pendentes.set(pendentes.get() + 1);
        Async.executar(trabalho, r -> {
            pendentes.set(pendentes.get() - 1);
            monitor.informarSucesso();
            ok.accept(r);
        }, erro -> {
            pendentes.set(pendentes.get() - 1);
            monitor.informarFalha(erro);
            falha.accept(erro);
        });
    }
}
