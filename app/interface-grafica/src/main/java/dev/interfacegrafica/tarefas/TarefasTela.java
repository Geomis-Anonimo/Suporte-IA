package dev.interfacegrafica.tarefas;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import dev.interfacegrafica.client.ApiException;
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
import dev.interfacegrafica.shell.Tela;
import dev.interfacegrafica.tarefas.TarefasViewModel.Estado;
import dev.interfacegrafica.tarefas.TarefasViewModel.Selecao;
import dev.interfacegrafica.ui.ApiMonitor;
import dev.interfacegrafica.ui.Confirmacao;
import dev.interfacegrafica.ui.Links;
import dev.interfacegrafica.ui.Notificacoes;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Tela "Tarefas": board estilo Azure (squads -> projetos -> tarefas) com arrastar e soltar. */
public class TarefasTela implements Tela, BoardView.Acoes {
    private static final Logger LOG = Logger.getLogger(TarefasTela.class.getName());
    private static final Preferences PREFS = Preferences.userRoot().node("dev/interfacegrafica/tarefas");
    private static final KeyCombination CTRL_N = new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination CTRL_F = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination F5 = new KeyCodeCombination(KeyCode.F5);

    /** No da arvore: squad, projeto ou "Sem projeto" de um squad. */
    private record No(SquadDto squad, ProjetoDto projeto, boolean semProjeto) {
        Selecao selecao() {
            return new Selecao(squad.id(), semProjeto ? FiltroTarefas.SEM_PROJETO : projeto == null ? null : projeto.id());
        }
    }

    private final TarefasViewModel vm;
    private final ApiMonitor monitor;
    private final Notificacoes notificacoes;

    private final BorderPane raiz = new BorderPane();
    private final SplitPane divisao = new SplitPane();
    private final TreeView<No> arvore = new TreeView<>();
    private final BoardView board = new BoardView(this);
    private final StackPane areaBoard = new StackPane();
    private final CustomTextField busca = new CustomTextField();
    private final MenuButton menuPrioridade = new MenuButton("Prioridade");
    private final MenuButton menuTags = new MenuButton("Tags");
    private final Button limparFiltros = new Button("Limpar filtros", new FontIcon(Feather.X));
    private final Set<Long> squadsExpandidos = new HashSet<>();
    private boolean atualizandoArvore;
    private boolean carregou;

    public TarefasTela(TarefasClient client, ApiMonitor monitor, Notificacoes notificacoes) {
        this.vm = new TarefasViewModel(client, monitor);
        this.monitor = monitor;
        this.notificacoes = notificacoes;
        montar();
    }

    @Override public String nome() { return "Tarefas"; }

    @Override public Ikon icone() { return Feather.TRELLO; }

    @Override public Node conteudo() { return raiz; }

    @Override
    public void aoMostrar() {
        if (!carregou) {
            carregou = true;
            long squad = PREFS.getLong("squadId", -1);
            long projeto = PREFS.getLong("projetoId", -1);
            vm.recarregar(squad < 0 ? null : new Selecao(squad, projeto < 0 ? null : projeto), this::falhaCarga);
        }
    }

    // ------------------------------------------------------------- montagem

    private void montar() {
        raiz.getStyleClass().add("tela");
        divisao.getItems().setAll(painelArvore(), painelBoard());
        divisao.setDividerPositions(PREFS.getDouble("divisor", 0.2));
        SplitPane.setResizableWithParent(divisao.getItems().get(0), false);
        divisao.getDividers().get(0).positionProperty().addListener((o, a, b) -> PREFS.putDouble("divisor", b.doubleValue()));
        raiz.setCenter(divisao);

        vm.squads().addListener((ListChangeListener<SquadDto>) c -> reconstruirArvore());
        vm.projetos().addListener((ListChangeListener<ProjetoDto>) c -> reconstruirArvore());
        vm.tarefas().addListener((ListChangeListener<TarefaDto>) c -> redesenhar());
        vm.filtroProperty().addListener((o, a, b) -> redesenhar());
        vm.selecionadaIdProperty().addListener((o, a, b) -> redesenhar());
        vm.estadoProperty().addListener((o, a, b) -> redesenhar());
        vm.selecaoProperty().addListener((o, antiga, nova) -> {
            selecionarNaArvore(nova);
            PREFS.putLong("squadId", nova.squadId() == null ? -1 : nova.squadId());
            PREFS.putLong("projetoId", nova.projetoId() == null ? -1 : nova.projetoId());
            if (!Objects.equals(antiga.squadId(), nova.squadId())) vm.recarregarTarefas(this::falhaCarga);
        });

        monitor.statusProperty().addListener((o, a, s) -> {
            if (s == ApiMonitor.Status.ONLINE && vm.estadoProperty().get() == Estado.OFFLINE) recarregar();
        });

        javafx.event.EventHandler<KeyEvent> atalhos = this::atalho;
        raiz.sceneProperty().addListener((o, antiga, nova) -> {
            if (antiga != null) antiga.removeEventFilter(KeyEvent.KEY_PRESSED, atalhos);
            if (nova != null) nova.addEventFilter(KeyEvent.KEY_PRESSED, atalhos);
        });
        redesenhar();
    }

    private Node painelArvore() {
        Label titulo = new Label("Squads");
        titulo.getStyleClass().add("titulo-arvore");
        Button novoSquad = new Button(null, new FontIcon(Feather.PLUS));
        novoSquad.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        novoSquad.setTooltip(new Tooltip("Novo squad"));
        novoSquad.setOnAction(e -> editarSquad(null));
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox cabecalho = new HBox(6, titulo, espaco, novoSquad);
        cabecalho.setAlignment(Pos.CENTER_LEFT);

        arvore.setShowRoot(false);
        arvore.setRoot(new TreeItem<>());
        arvore.getStyleClass().add("arvore-squads");
        arvore.setCellFactory(t -> new CelulaNo());
        arvore.getSelectionModel().selectedItemProperty().addListener((o, a, item) -> {
            if (atualizandoArvore || item == null || item.getValue() == null) return;
            vm.selecaoProperty().set(item.getValue().selecao());
        });
        VBox.setVgrow(arvore, Priority.ALWAYS);

        CheckBox arquivados = new CheckBox("Mostrar arquivados");
        arquivados.getStyleClass().add("dica");
        arquivados.selectedProperty().bindBidirectional(vm.mostrarArquivadosProperty());
        arquivados.selectedProperty().addListener((o, a, b) -> recarregar());

        VBox painel = new VBox(8, cabecalho, arvore, arquivados);
        painel.getStyleClass().add("painel-arvore");
        painel.setMinWidth(200);
        return painel;
    }

    private Node painelBoard() {
        busca.setPromptText("Buscar (título, tags, motivo, projeto…)");
        busca.setLeft(new FontIcon(Feather.SEARCH));
        busca.setPrefWidth(280);
        busca.setMinWidth(150);
        HBox.setHgrow(busca, Priority.SOMETIMES);
        vm.buscaProperty().bind(busca.textProperty());
        busca.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) busca.clear();
        });

        menuPrioridade.setGraphic(new FontIcon(Feather.FLAG));
        for (Prioridade p : Prioridade.values()) {
            CheckMenuItem item = new CheckMenuItem(p.rotulo);
            item.selectedProperty().addListener((o, a, sim) -> {
                if (sim) vm.prioridades().add(p);
                else vm.prioridades().remove(p);
            });
            vm.prioridades().addListener((SetChangeListener<Prioridade>) c -> item.setSelected(vm.prioridades().contains(p)));
            menuPrioridade.getItems().add(item);
        }
        menuTags.setGraphic(new FontIcon(Feather.TAG));
        menuTags.setOnShowing(e -> montarMenuTags());
        menuTags.getItems().add(new MenuItem("(carregando)"));

        limparFiltros.getStyleClass().addAll(Styles.FLAT, Styles.SMALL);
        limparFiltros.setOnAction(e -> {
            busca.clear();
            vm.limparFiltros();
        });

        Button nova = new Button("Nova tarefa", new FontIcon(Feather.PLUS));
        nova.getStyleClass().add(Styles.ACCENT);
        nova.setTooltip(new Tooltip("Nova tarefa (Ctrl+N)"));
        nova.setOnAction(e -> novaTarefa(Status.A_FAZER));
        Button atualizar = new Button(null, new FontIcon(Feather.REFRESH_CW));
        atualizar.getStyleClass().add(Styles.BUTTON_ICON);
        atualizar.setTooltip(new Tooltip("Atualizar (F5)"));
        atualizar.setOnAction(e -> recarregar());
        atualizar.disableProperty().bind(vm.ocupadoProperty());

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox barra = new HBox(8, busca, menuPrioridade, menuTags, limparFiltros, espaco, nova,
                new Separator(Orientation.VERTICAL), atualizar);
        barra.getStyleClass().add("barra-ferramentas");
        barra.setAlignment(Pos.CENTER_LEFT);

        ProgressBar carregando = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        carregando.getStyleClass().addAll(Styles.SMALL, "barra-carregando");
        carregando.setMaxWidth(Double.MAX_VALUE);
        carregando.visibleProperty().bind(vm.ocupadoProperty());

        VBox.setVgrow(areaBoard, Priority.ALWAYS);
        VBox painel = new VBox(10, barra, carregando, areaBoard);
        painel.getStyleClass().add("painel-board");
        return painel;
    }

    private void montarMenuTags() {
        List<String> usadas = FiltroTarefas.tagsUsadas(vm.tarefas());
        menuTags.getItems().clear();
        if (usadas.isEmpty()) {
            MenuItem vazio = new MenuItem("Nenhuma tag neste squad");
            vazio.setDisable(true);
            menuTags.getItems().add(vazio);
            return;
        }
        for (String tag : usadas) {
            CheckMenuItem item = new CheckMenuItem("#" + tag);
            item.setSelected(vm.tags().contains(tag));
            item.selectedProperty().addListener((o, a, sim) -> {
                if (sim) vm.tags().add(tag);
                else vm.tags().remove(tag);
            });
            menuTags.getItems().add(item);
        }
    }

    // ------------------------------------------------------------- arvore

    private void reconstruirArvore() {
        atualizandoArvore = true;
        try {
            for (TreeItem<No> item : arvore.getRoot().getChildren()) {
                if (item.isExpanded()) squadsExpandidos.add(item.getValue().squad().id());
                else squadsExpandidos.remove(item.getValue().squad().id());
            }
            Long selecionado = vm.selecaoProperty().get().squadId();
            arvore.getRoot().getChildren().clear();
            for (SquadDto s : vm.squads()) {
                TreeItem<No> itemSquad = new TreeItem<>(new No(s, null, false));
                for (ProjetoDto p : vm.projetos()) {
                    if (p.squadId() == s.id()) itemSquad.getChildren().add(new TreeItem<>(new No(s, p, false)));
                }
                itemSquad.getChildren().add(new TreeItem<>(new No(s, null, true)));
                itemSquad.setExpanded(squadsExpandidos.contains(s.id()) || s.id().equals(selecionado));
                arvore.getRoot().getChildren().add(itemSquad);
            }
            selecionarNaArvore(vm.selecaoProperty().get());
        } finally {
            atualizandoArvore = false;
        }
    }

    private void selecionarNaArvore(Selecao s) {
        boolean antes = atualizandoArvore;
        atualizandoArvore = true;
        try {
            for (TreeItem<No> itemSquad : arvore.getRoot().getChildren()) {
                if (!itemSquad.getValue().squad().id().equals(s.squadId())) continue;
                itemSquad.setExpanded(true); // o squad selecionado sempre mostra os projetos
                if (s.projetoId() == null) {
                    arvore.getSelectionModel().select(itemSquad);
                    return;
                }
                itemSquad.setExpanded(true);
                for (TreeItem<No> filho : itemSquad.getChildren()) {
                    if (filho.getValue().selecao().equals(s)) {
                        arvore.getSelectionModel().select(filho);
                        return;
                    }
                }
            }
        } finally {
            atualizandoArvore = antes;
        }
    }

    /** Contagem de abertas do "Sem projeto": total do squad menos o dos projetos. */
    private long abertasSemProjeto(SquadDto s) {
        long projetos = vm.projetos().stream().filter(p -> p.squadId() == s.id()).mapToLong(ProjetoDto::abertas).sum();
        return Math.max(0, s.abertas() - projetos);
    }

    private final class CelulaNo extends TreeCell<No> {
        @Override
        protected void updateItem(No no, boolean vazio) {
            super.updateItem(no, vazio);
            getStyleClass().removeAll("no-squad", "no-projeto", "no-sem-projeto", "arquivado");
            if (vazio || no == null) {
                setGraphic(null);
                setText(null);
                setContextMenu(null);
                return;
            }
            String texto;
            long abertas;
            Feather icone;
            boolean arquivado;
            if (no.projeto() != null) {
                texto = no.projeto().nome();
                abertas = no.projeto().abertas();
                icone = Feather.FOLDER;
                arquivado = no.projeto().arquivado();
                getStyleClass().add("no-projeto");
            } else if (no.semProjeto()) {
                texto = "Sem projeto";
                abertas = abertasSemProjeto(no.squad());
                icone = Feather.INBOX;
                arquivado = false;
                getStyleClass().add("no-sem-projeto");
            } else {
                texto = no.squad().nome();
                abertas = no.squad().abertas();
                icone = Feather.USERS;
                arquivado = no.squad().arquivado();
                getStyleClass().add("no-squad");
            }
            if (arquivado) getStyleClass().add("arquivado");
            Label nome = new Label(texto, new FontIcon(icone));
            nome.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nome, Priority.ALWAYS);
            Label contador = new Label(String.valueOf(abertas));
            contador.getStyleClass().add("contador-arvore");
            contador.setVisible(abertas > 0);
            contador.setManaged(abertas > 0);
            Tooltip.install(contador, new Tooltip(abertas + " tarefa(s) em aberto"));
            HBox caixa = new HBox(6, nome, contador);
            caixa.setAlignment(Pos.CENTER_LEFT);
            setText(null);
            setGraphic(caixa);
            setTooltip(no.projeto() != null && no.projeto().contextoMd() != null ? new Tooltip(no.projeto().contextoMd()) : null);
            setContextMenu(menuNo(no));
        }
    }

    private ContextMenu menuNo(No no) {
        MenuItem novaAqui = item("Nova tarefa aqui", Feather.PLUS, () -> {
            vm.selecaoProperty().set(no.selecao());
            novaTarefa(Status.A_FAZER);
        });
        if (no.semProjeto()) return new ContextMenu(novaAqui);
        if (no.projeto() != null) {
            ProjetoDto p = no.projeto();
            MenuItem abrirCtx = item("Abrir contexto (.md)", Feather.FILE_TEXT, () -> abrir(p.contextoMd()));
            MenuItem copiarCtx = item("Copiar caminho do contexto", Feather.COPY, () -> {
                ClipboardContent c = new ClipboardContent();
                c.putString(p.contextoMd());
                Clipboard.getSystemClipboard().setContent(c);
                notificacoes.info("Caminho copiado: " + p.contextoMd());
            });
            abrirCtx.setDisable(p.contextoMd() == null);
            copiarCtx.setDisable(p.contextoMd() == null);
            return new ContextMenu(novaAqui, new SeparatorMenuItem(),
                    item("Editar projeto", Feather.EDIT_2, () -> editarProjeto(p, null)),
                    item(p.arquivado() ? "Desarquivar" : "Arquivar", Feather.ARCHIVE, () -> arquivarProjeto(p)),
                    item("Excluir projeto", Feather.TRASH_2, () -> excluirProjeto(p)),
                    new SeparatorMenuItem(), abrirCtx, copiarCtx);
        }
        SquadDto s = no.squad();
        return new ContextMenu(novaAqui, item("Novo projeto", Feather.FOLDER_PLUS, () -> editarProjeto(null, s)),
                new SeparatorMenuItem(),
                item("Editar squad", Feather.EDIT_2, () -> editarSquad(s)),
                item(s.arquivado() ? "Desarquivar" : "Arquivar", Feather.ARCHIVE, () -> arquivarSquad(s)),
                item("Excluir squad", Feather.TRASH_2, () -> excluirSquad(s)));
    }

    // ------------------------------------------------------------- board

    private void redesenhar() {
        limparFiltros.setVisible(vm.filtroProperty().get().ativo());
        limparFiltros.setManaged(limparFiltros.isVisible());
        Estado e = vm.estadoProperty().get();
        if (e == Estado.OFFLINE) {
            areaBoard.getChildren().setAll(vazio(Feather.WIFI_OFF, "API fora do ar", Erros.DICA_DOCKER, "Tentar de novo",
                    this::recarregar));
            return;
        }
        if (e == Estado.ERRO && vm.squads().isEmpty()) {
            areaBoard.getChildren().setAll(vazio(Feather.ALERT_TRIANGLE, "Não foi possível carregar o board",
                    "Veja a mensagem de erro.", "Tentar de novo", this::recarregar));
            return;
        }
        if (e != Estado.CARREGANDO && vm.squads().isEmpty()) {
            areaBoard.getChildren().setAll(vazio(Feather.USERS, "Crie o primeiro squad",
                    "Um squad é um time (ex.: Guardian). Os projetos e as tarefas ficam dentro dele.", "Novo squad",
                    () -> editarSquad(null)));
            return;
        }
        Map<Status, List<TarefaDto>> porColuna = new EnumMap<>(Status.class);
        for (Status s : Status.values()) porColuna.put(s, vm.coluna(s));
        SquadDto squad = vm.squadSelecionado();
        board.exibir(porColuna, vm.selecionadaIdProperty().get(), vm.selecaoProperty().get().projetoId() == null,
                vm.feitoTodasProperty().get(), squad != null && !squad.arquivado());
        if (!areaBoard.getChildren().contains(board)) areaBoard.getChildren().setAll(board);
    }

    private Node vazio(Feather icone, String titulo, String texto, String acao, Runnable aoClicar) {
        Label t = new Label(titulo);
        t.getStyleClass().add("vazio-titulo");
        Label d = new Label(texto);
        d.getStyleClass().add("dica");
        d.setWrapText(true);
        VBox caixa = new VBox(8, new FontIcon(icone), t, d);
        caixa.getStyleClass().add("vazio");
        caixa.setAlignment(Pos.CENTER);
        caixa.setMaxWidth(420);
        if (acao != null) {
            Button b = new Button(acao);
            b.getStyleClass().add(Styles.ACCENT);
            b.setOnAction(e -> aoClicar.run());
            caixa.getChildren().add(b);
        }
        return caixa;
    }

    // ------------------------------------------------------------- BoardView.Acoes

    @Override
    public void selecionar(TarefaDto t) {
        vm.selecionadaIdProperty().set(t.id());
        board.requestFocus();
    }

    @Override
    public void editar(TarefaDto t) {
        abrirFormulario(TarefaFormDialog.Modo.EDITAR, t, null);
    }

    @Override
    public ContextMenu menu(TarefaDto t) {
        Menu moverPara = new Menu("Mover para", new FontIcon(Feather.ARROW_RIGHT));
        int n = 1;
        for (Status s : Status.values()) {
            MenuItem m = new MenuItem(s.rotulo + "   (Ctrl+" + n++ + ")");
            m.setDisable(s == t.status());
            m.setOnAction(e -> mover(t.id(), s, null));
            moverPara.getItems().add(m);
        }
        MenuItem abrirLink = item("Abrir link", Feather.EXTERNAL_LINK, () -> abrirLink(t));
        abrirLink.setDisable(t.link() == null);
        return new ContextMenu(item("Editar", Feather.EDIT_2, () -> editar(t)), moverPara, abrirLink,
                item("Duplicar", Feather.COPY, () -> abrirFormulario(TarefaFormDialog.Modo.DUPLICAR, t, null)),
                new SeparatorMenuItem(), item("Excluir", Feather.TRASH_2, () -> excluirTarefa(t)));
    }

    @Override
    public void abrirLink(TarefaDto t) {
        abrir(t.link());
    }

    @Override
    public void mover(long id, Status status, Long antesDeId) {
        TarefaDto t = vm.tarefa(id);
        if (t == null) return;
        String motivo = null;
        if (status == Status.BLOQUEADO && t.status() != Status.BLOQUEADO) {
            var resposta = CadastroDialogs.motivoBloqueio(janela(), t.titulo(), null);
            if (resposta.isEmpty()) return; // cancelou: o card fica onde estava
            motivo = resposta.get();
        }
        vm.mover(id, status, antesDeId, motivo, movida -> { }, erro -> tratarErro("Mover tarefa", erro));
    }

    @Override
    public void criarRapida(String titulo) {
        Selecao s = vm.selecaoProperty().get();
        if (s.squadId() == null) return;
        Long projeto = s.projetoId() == null || s.projetoId() == FiltroTarefas.SEM_PROJETO ? null : s.projetoId();
        var req = new TarefaRequest(s.squadId(), projeto, titulo, null, Status.A_FAZER, null, Prioridade.MEDIA, List.of(), null);
        vm.salvarTarefa(null, req, ok -> notificacoes.sucesso("Tarefa criada."), erro -> tratarErro("Criar tarefa", erro));
    }

    @Override
    public void alternarFeitoTodas() {
        vm.feitoTodasProperty().set(!vm.feitoTodasProperty().get());
        vm.recarregarTarefas(this::falhaCarga);
    }

    // ------------------------------------------------------------- acoes de tarefa

    private void novaTarefa(Status status) {
        if (vm.squads().isEmpty()) {
            editarSquad(null);
            return;
        }
        abrirFormulario(TarefaFormDialog.Modo.NOVA, null, status);
    }

    private void abrirFormulario(TarefaFormDialog.Modo modo, TarefaDto base, Status statusInicial) {
        Selecao s = vm.selecaoProperty().get();
        Long projeto = s.projetoId() == null || s.projetoId() == FiltroTarefas.SEM_PROJETO ? null : s.projetoId();
        Long id = modo == TarefaFormDialog.Modo.EDITAR ? base.id() : null;
        TarefaFormDialog dialogo = new TarefaFormDialog(janela(), modo, base, s.squadId(), projeto, statusInicial,
                vm.squads(), vm.projetos(), FiltroTarefas.tagsUsadas(vm.tarefas()), this::abrir,
                (req, ok, falha) -> vm.salvarTarefa(id, req, ok, falha));
        dialogo.showAndWait().ifPresent(salva -> {
            if (!Objects.equals(salva.squadId(), s.squadId())) {
                vm.selecaoProperty().set(new Selecao(salva.squadId(), null));
            }
            notificacoes.sucesso(modo == TarefaFormDialog.Modo.EDITAR ? "Tarefa salva." : "Tarefa criada.");
        });
    }

    private void excluirTarefa(TarefaDto t) {
        if (!Confirmacao.confirmar(janela(), "Excluir tarefa",
                "Excluir a tarefa \"" + t.titulo() + "\"? Essa ação não pode ser desfeita.", "Excluir", true)) return;
        vm.excluirTarefa(t.id(), () -> notificacoes.sucesso("Tarefa excluída."), erro -> tratarErro("Excluir tarefa", erro));
    }

    // ------------------------------------------------------------- squads e projetos

    private void editarSquad(SquadDto base) {
        CadastroDialogs.squad(janela(), base, (req, ok, falha) -> vm.salvarSquad(base == null ? null : base.id(), req, ok, falha))
                .ifPresent(s -> notificacoes.sucesso("Squad \"" + s.nome() + "\" salvo."));
    }

    private void arquivarSquad(SquadDto s) {
        var req = new SquadRequest(s.slug(), s.nome(), s.ordem(), !s.arquivado());
        vm.salvarSquad(s.id(), req, ok -> notificacoes.sucesso(s.arquivado() ? "Squad desarquivado." : "Squad arquivado."),
                erro -> tratarErro("Arquivar squad", erro));
    }

    private void excluirSquad(SquadDto s) {
        if (!Confirmacao.confirmar(janela(), "Excluir squad", "Excluir o squad \"" + s.nome()
                + "\"? Só é possível se ele não tiver projetos nem tarefas.", "Excluir", true)) return;
        vm.excluirSquad(s.id(), () -> notificacoes.sucesso("Squad excluído."), erro -> tratarErro("Excluir squad", erro));
    }

    private void editarProjeto(ProjetoDto base, SquadDto squadInicial) {
        CadastroDialogs.projeto(janela(), base, squadInicial, vm.squads(),
                        (req, ok, falha) -> vm.salvarProjeto(base == null ? null : base.id(), req, ok, falha))
                .ifPresent(p -> notificacoes.sucesso("Projeto \"" + p.nome() + "\" salvo."));
    }

    private void arquivarProjeto(ProjetoDto p) {
        var req = new ProjetoRequest(p.squadId(), p.slug(), p.nome(), p.descricao(), p.contextoMd(), !p.arquivado());
        vm.salvarProjeto(p.id(), req, ok -> notificacoes.sucesso(p.arquivado() ? "Projeto desarquivado." : "Projeto arquivado."),
                erro -> tratarErro("Arquivar projeto", erro));
    }

    private void excluirProjeto(ProjetoDto p) {
        if (!Confirmacao.confirmar(janela(), "Excluir projeto", "Excluir o projeto \"" + p.nome()
                + "\"? Só é possível se ele não tiver tarefas.", "Excluir", true)) return;
        vm.excluirProjeto(p.id(), () -> notificacoes.sucesso("Projeto excluído."), erro -> tratarErro("Excluir projeto", erro));
    }

    // ------------------------------------------------------------- teclado

    private void atalho(KeyEvent e) {
        if (raiz.getParent() == null) return; // tela nao visivel
        Node foco = raiz.getScene() == null ? null : raiz.getScene().getFocusOwner();
        // Delete/Enter so agem no card quando o foco esta no board (nao na arvore nem num campo de texto)
        boolean digitando = !(foco == board || foco == null);
        TarefaDto sel = vm.tarefa(vm.selecionadaIdProperty().get());
        if (CTRL_N.match(e)) {
            novaTarefa(Status.A_FAZER);
        } else if (F5.match(e)) {
            recarregar();
        } else if (CTRL_F.match(e)) {
            busca.requestFocus();
            busca.selectAll();
        } else if (sel != null && !digitando && e.getCode() == KeyCode.DELETE) {
            excluirTarefa(sel);
        } else if (sel != null && !digitando && e.getCode() == KeyCode.ENTER) {
            editar(sel);
        } else if (sel != null && e.isShortcutDown() && e.getCode().isDigitKey()) {
            int n = e.getCode().getCode() - KeyCode.DIGIT1.getCode();
            if (n < 0 || n >= Status.values().length) return;
            Status destino = Status.values()[n];
            if (destino != sel.status()) mover(sel.id(), destino, null);
        } else {
            return;
        }
        e.consume();
    }

    // ------------------------------------------------------------- utilidades

    private void recarregar() {
        vm.recarregar(null, this::falhaCarga);
    }

    private void falhaCarga(Throwable erro) {
        if (erro instanceof ApiOfflineException) notificacoes.erro("API fora do ar. " + Erros.DICA_DOCKER);
        else tratarErro("Carregar o board", erro);
    }

    private void tratarErro(String acao, Throwable erro) {
        LOG.log(Level.WARNING, acao + " falhou", erro);
        notificacoes.erro(acao + ": " + Erros.mensagem(erro));
        if (erro instanceof ApiException api && api.status() == 404) Platform.runLater(this::recarregar);
    }

    private void abrir(String link) {
        Links.abrir(link, msg -> notificacoes.erro(msg));
    }

    private Window janela() {
        return raiz.getScene() == null ? null : raiz.getScene().getWindow();
    }

    private static MenuItem item(String texto, Feather icone, Runnable acao) {
        MenuItem m = new MenuItem(texto, new FontIcon(icone));
        m.setOnAction(e -> acao.run());
        return m;
    }
}
