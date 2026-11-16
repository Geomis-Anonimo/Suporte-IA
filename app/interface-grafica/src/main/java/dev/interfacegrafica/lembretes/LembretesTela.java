package dev.interfacegrafica.lembretes;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import dev.interfacegrafica.client.ApiException;
import dev.interfacegrafica.client.ApiOfflineException;
import dev.interfacegrafica.client.LembretesClient;
import dev.interfacegrafica.client.dto.LembreteResponse;
import dev.interfacegrafica.client.dto.ParticipanteDto;
import dev.interfacegrafica.config.AppConfig;
import dev.interfacegrafica.lembretes.LembreteFormDialog.Modo;
import dev.interfacegrafica.lembretes.LembretesViewModel.Estado;
import dev.interfacegrafica.lembretes.LembretesViewModel.Filtro;
import dev.interfacegrafica.shell.Tela;
import dev.interfacegrafica.ui.ApiMonitor;
import dev.interfacegrafica.ui.Confirmacao;
import dev.interfacegrafica.ui.Notificacoes;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Tela "Lembretes": lista, busca, filtro, detalhes e acoes de CRUD + testar. */
public class LembretesTela implements Tela {
    private static final Logger LOG = Logger.getLogger(LembretesTela.class.getName());
    private static final PseudoClass INATIVO = PseudoClass.getPseudoClass("inativo");
    private static final String DICA_DOCKER = "Rode \"docker compose up -d\" na pasta CLAUDE.";
    private static final KeyCombination CTRL_N = new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination CTRL_F = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination F5 = new KeyCodeCombination(KeyCode.F5);

    private final LembretesViewModel vm;
    private final ApiMonitor monitor;
    private final Notificacoes notificacoes;

    private final BorderPane raiz = new BorderPane();
    private final TableView<LembreteResponse> tabela = new TableView<>();
    private final CustomTextField busca = new CustomTextField();
    private final SplitPane divisao = new SplitPane();
    private final VBox detalhes = new VBox();
    private final ScrollPane detalhesRolagem = new ScrollPane(detalhes);
    private boolean carregou;

    public LembretesTela(LembretesClient client, ApiMonitor monitor, Notificacoes notificacoes) {
        this.vm = new LembretesViewModel(client, monitor);
        this.monitor = monitor;
        this.notificacoes = notificacoes;
        montar();
    }

    @Override public String nome() { return "Lembretes"; }

    @Override public Ikon icone() { return Feather.BELL; }

    @Override public Node conteudo() { return raiz; }

    @Override
    public void aoMostrar() {
        if (!carregou) {
            carregou = true;
            recarregar();
        }
        javafx.application.Platform.runLater(tabela::requestFocus);
    }

    // ---------------------------------------------------------------- montagem

    private void montar() {
        raiz.getStyleClass().add("tela");
        ProgressBar carregando = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        carregando.getStyleClass().addAll(Styles.SMALL, "barra-carregando");
        carregando.setMaxWidth(Double.MAX_VALUE);
        carregando.visibleProperty().bind(vm.ocupadoProperty());

        VBox topo = new VBox(10, barraFerramentas(), carregando);
        raiz.setTop(topo);

        montarTabela();
        montarDetalhes();
        divisao.getItems().setAll(tabela, detalhesRolagem);
        divisao.setDividerPositions(0.74);
        SplitPane.setResizableWithParent(detalhesRolagem, false);
        raiz.setCenter(divisao);

        // atalhos valem na janela toda (o foco pode estar no menu lateral), mas so com esta tela visivel
        javafx.event.EventHandler<KeyEvent> atalhos = e -> {
            if (raiz.getParent() == null) return;
            if (CTRL_N.match(e)) {
                novo();
                e.consume();
            } else if (F5.match(e)) {
                recarregar();
                e.consume();
            } else if (CTRL_F.match(e)) {
                busca.requestFocus();
                busca.selectAll();
                e.consume();
            }
        };
        raiz.sceneProperty().addListener((obs, antiga, nova) -> {
            if (antiga != null) antiga.removeEventFilter(KeyEvent.KEY_PRESSED, atalhos);
            if (nova != null) nova.addEventFilter(KeyEvent.KEY_PRESSED, atalhos);
        });

        vm.estadoProperty().addListener((obs, a, b) -> atualizarPlaceholder());
        vm.todos().addListener((ListChangeListener<LembreteResponse>) c -> atualizarPlaceholder());
        vm.filtrados().addListener((ListChangeListener<LembreteResponse>) c -> atualizarPlaceholder());
        atualizarPlaceholder();

        // API voltou: recarrega sozinho se a lista estava no estado offline
        monitor.statusProperty().addListener((obs, a, s) -> {
            if (s == ApiMonitor.Status.ONLINE && vm.estadoProperty().get() == Estado.OFFLINE) recarregar();
        });

        // "em 2 h", "amanhã"... envelhecem: redesenha a cada minuto
        Timeline relogio = new Timeline(new KeyFrame(Duration.minutes(1), e -> {
            tabela.refresh();
            mostrarDetalhes(selecionado());
        }));
        relogio.setCycleCount(Animation.INDEFINITE);
        relogio.play();
    }

    private HBox barraFerramentas() {
        busca.setPromptText("Buscar (título, mensagem, participantes…)");
        busca.setLeft(new FontIcon(Feather.SEARCH));
        busca.setPrefWidth(300);
        busca.setMinWidth(160);
        HBox.setHgrow(busca, Priority.SOMETIMES);
        vm.buscaProperty().bind(busca.textProperty());
        busca.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) busca.clear();
        });

        ToggleGroup grupo = new ToggleGroup();
        HBox pilulas = new HBox();
        Filtro[] filtros = Filtro.values();
        for (int i = 0; i < filtros.length; i++) {
            ToggleButton b = new ToggleButton(filtros[i].rotulo);
            b.getStyleClass().add(i == 0 ? Styles.LEFT_PILL : i == filtros.length - 1 ? Styles.RIGHT_PILL : Styles.CENTER_PILL);
            b.setUserData(filtros[i]);
            b.setToggleGroup(grupo);
            b.setSelected(filtros[i] == vm.filtroProperty().get());
            pilulas.getChildren().add(b);
        }
        grupo.selectedToggleProperty().addListener((obs, antigo, novo) -> {
            if (novo == null) {
                grupo.selectToggle(antigo);
                return;
            }
            vm.filtroProperty().set((Filtro) novo.getUserData());
            recarregar();
        });

        BooleanBinding semSelecao = tabela.getSelectionModel().selectedItemProperty().isNull();
        BooleanBinding bloqueado = semSelecao.or(vm.ocupadoProperty());

        Button novo = botao("Novo", Feather.PLUS, "Novo lembrete (Ctrl+N)", e -> novo());
        novo.getStyleClass().add(Styles.ACCENT);
        Button editar = botaoIcone(Feather.EDIT_2, "Editar (Enter ou duplo-clique)", e -> editar(selecionado()));
        Button duplicar = botaoIcone(Feather.COPY, "Duplicar", e -> duplicar(selecionado()));
        Button testar = botaoIcone(Feather.SEND, "Testar no Discord (envia o aviso agora)", e -> testar(selecionado()));
        Button excluir = botaoIcone(Feather.TRASH_2, "Excluir (Delete)", e -> excluir(selecionado()));
        excluir.getStyleClass().add(Styles.DANGER);
        for (Button b : new Button[]{editar, duplicar, testar, excluir}) b.disableProperty().bind(bloqueado);
        novo.disableProperty().bind(vm.ocupadoProperty());

        Button atualizar = botaoIcone(Feather.REFRESH_CW, "Atualizar (F5)", e -> recarregar());
        atualizar.disableProperty().bind(vm.ocupadoProperty());
        ToggleButton painel = new ToggleButton(null, new FontIcon(Feather.SIDEBAR));
        painel.getStyleClass().add(Styles.BUTTON_ICON);
        painel.setTooltip(new Tooltip("Mostrar/ocultar detalhes"));
        painel.setSelected(true);
        painel.selectedProperty().addListener((obs, a, mostrar) -> {
            if (mostrar && !divisao.getItems().contains(detalhesRolagem)) {
                divisao.getItems().add(detalhesRolagem);
                divisao.setDividerPositions(0.74);
            } else if (!mostrar) {
                divisao.getItems().remove(detalhesRolagem);
            }
        });

        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        HBox barra = new HBox(8, busca, pilulas, espaco, novo, editar, duplicar, testar, excluir,
                new Separator(javafx.geometry.Orientation.VERTICAL), atualizar, painel);
        barra.getStyleClass().add("barra-ferramentas");
        barra.setAlignment(Pos.CENTER_LEFT);
        return barra;
    }

    private void montarTabela() {
        TableColumn<LembreteResponse, LembreteResponse> colTitulo = new TableColumn<>("Título");
        colTitulo.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colTitulo.setComparator(Comparator.comparing(l -> TextoBusca.normalizar(l.titulo())));
        colTitulo.setCellFactory(c -> new TableCell<>() {
            private final Label nome = new Label();
            private final Label desc = new Label();
            private final VBox caixa = new VBox(2, nome, desc);

            {
                nome.getStyleClass().add("titulo-celula");
                desc.getStyleClass().add("descricao-celula");
                caixa.setAlignment(Pos.CENTER_LEFT);
                setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(LembreteResponse l, boolean vazio) {
                super.updateItem(l, vazio);
                if (vazio || l == null) {
                    setGraphic(null);
                    return;
                }
                nome.setText(l.titulo());
                boolean temDesc = l.descricao() != null && !l.descricao().isBlank();
                desc.setText(temDesc ? l.descricao() : "");
                desc.setVisible(temDesc);
                desc.setManaged(temDesc);
                setGraphic(caixa);
            }
        });
        colTitulo.setPrefWidth(280);
        colTitulo.setMinWidth(180);

        TableColumn<LembreteResponse, String> colQuando = new TableColumn<>("Quando");
        colQuando.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(RecorrenciaFormatter.formatar(c.getValue().recorrencia())));
        colQuando.setPrefWidth(160);
        colQuando.setMinWidth(150);

        TableColumn<LembreteResponse, OffsetDateTime> colProximo = new TableColumn<>("Próximo aviso");
        colProximo.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().proximoDisparo()));
        colProximo.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));
        colProximo.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(OffsetDateTime quando, boolean vazio) {
                super.updateItem(quando, vazio);
                if (vazio) {
                    setText(null);
                } else if (quando == null) {
                    setText("—");
                } else {
                    setText(RecorrenciaFormatter.diaMesHora(quando, AppConfig.FUSO) + " · "
                            + RecorrenciaFormatter.relativo(quando, agora()));
                }
            }
        });
        colProximo.setPrefWidth(180);
        colProximo.setMinWidth(170);

        TableColumn<LembreteResponse, Integer> colAntecedencia = new TableColumn<>("Antes");
        colAntecedencia.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().antecedenciaMin()));
        colAntecedencia.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Integer min, boolean vazio) {
                super.updateItem(min, vazio);
                setText(vazio || min == null ? null : min + " min");
            }
        });
        colAntecedencia.setPrefWidth(75);
        colAntecedencia.setMinWidth(70);

        TableColumn<LembreteResponse, String> colParticipantes = new TableColumn<>("Participantes");
        colParticipantes.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().participantesOuVazio().stream()
                .map(ParticipanteDto::nome).collect(Collectors.joining(", "))));
        colParticipantes.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String nomes, boolean vazio) {
                super.updateItem(nomes, vazio);
                boolean tem = !vazio && nomes != null && !nomes.isBlank();
                setText(vazio ? null : tem ? nomes : "—");
                setTooltip(tem ? new Tooltip(nomes) : null);
            }
        });
        colParticipantes.setPrefWidth(180);
        colParticipantes.setMinWidth(100);

        TableColumn<LembreteResponse, Boolean> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().ativo()));
        colStatus.setCellFactory(c -> new TableCell<>() {
            private final Label selo = new Label();

            @Override
            protected void updateItem(Boolean ativo, boolean vazio) {
                super.updateItem(ativo, vazio);
                if (vazio || ativo == null) {
                    setGraphic(null);
                    return;
                }
                selo.setText(ativo ? "Ativo" : "Inativo");
                selo.getStyleClass().setAll("label", "selo", ativo ? "selo-ativo" : "selo-inativo");
                setGraphic(selo);
            }
        });
        colStatus.setPrefWidth(90);
        colStatus.setMinWidth(90);

        tabela.getColumns().setAll(java.util.List.of(colTitulo, colQuando, colProximo, colAntecedencia, colParticipantes, colStatus));
        // colunas ocupam a largura toda, respeitando a largura minima de cada uma
        tabela.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tabela.getStyleClass().addAll(Styles.STRIPED);

        SortedList<LembreteResponse> ordenada = new SortedList<>(vm.filtrados());
        ordenada.comparatorProperty().bind(tabela.comparatorProperty());
        tabela.setItems(ordenada);
        colProximo.setSortType(TableColumn.SortType.ASCENDING);
        tabela.getSortOrder().add(colProximo);

        ContextMenu menu = new ContextMenu(
                item("Editar", Feather.EDIT_2, () -> editar(selecionado())),
                item("Duplicar", Feather.COPY, () -> duplicar(selecionado())),
                item("Testar no Discord", Feather.SEND, () -> testar(selecionado())),
                new SeparatorMenuItem(),
                item("Excluir", Feather.TRASH_2, () -> excluir(selecionado())));

        tabela.setRowFactory(tv -> {
            TableRow<LembreteResponse> linha = new TableRow<>() {
                @Override
                protected void updateItem(LembreteResponse l, boolean vazio) {
                    super.updateItem(l, vazio);
                    pseudoClassStateChanged(INATIVO, !vazio && l != null && !l.ativo());
                }
            };
            linha.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !linha.isEmpty()) editar(linha.getItem());
            });
            linha.contextMenuProperty().bind(Bindings.when(linha.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return linha;
        });

        tabela.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && selecionado() != null) {
                editar(selecionado());
                e.consume();
            } else if (e.getCode() == KeyCode.DELETE && selecionado() != null) {
                excluir(selecionado());
                e.consume();
            }
        });

        tabela.getSelectionModel().selectedItemProperty().addListener((obs, a, l) -> mostrarDetalhes(l));
    }

    private void montarDetalhes() {
        detalhes.getStyleClass().add("detalhes");
        detalhesRolagem.setFitToWidth(true);
        detalhesRolagem.setMinWidth(260);
        detalhesRolagem.setPrefWidth(300);
        detalhesRolagem.getStyleClass().add("detalhes-rolagem");
        mostrarDetalhes(null);
    }

    private void mostrarDetalhes(LembreteResponse l) {
        if (l == null) {
            Label vazio = new Label("Selecione um lembrete para ver os detalhes.");
            vazio.getStyleClass().add("dica");
            vazio.setWrapText(true);
            detalhes.getChildren().setAll(vazio);
            return;
        }
        Label titulo = new Label(l.titulo());
        titulo.getStyleClass().add(Styles.TITLE_4);
        titulo.setWrapText(true);
        Label quando = new Label(RecorrenciaFormatter.formatar(l.recorrencia()));
        quando.getStyleClass().add("dica");

        GridPane info = new GridPane();
        info.getStyleClass().add("detalhes-info");
        int r = 0;
        info.addRow(r++, rotulo("Próximo aviso"), new Label(dataComRelativo(l.proximoDisparo())));
        info.addRow(r++, rotulo("Ocorrência"), new Label(dataComRelativo(l.proximaOcorrencia())));
        info.addRow(r++, rotulo("Antecedência"), new Label(l.antecedenciaMin() + " min"));
        info.addRow(r, rotulo("Status"), new Label(l.ativo() ? "Ativo" : "Inativo"));

        VBox caixa = new VBox(12, titulo, quando, info);
        if (l.descricao() != null && !l.descricao().isBlank()) {
            Label d = new Label(l.descricao());
            d.setWrapText(true);
            caixa.getChildren().add(bloco("Descrição", d));
        }

        String texto = l.mensagem() == null || l.mensagem().isBlank() ? "" : l.mensagem();
        TextArea msg = new TextArea(texto.isEmpty() ? "(sem mensagem: o aviso usa a descrição)" : texto);
        msg.setEditable(false);
        msg.setWrapText(true);
        msg.setPrefRowCount(Math.min(14, Math.max(4, texto.length() / 45 + 1)));
        msg.getStyleClass().add("mensagem-detalhe");
        caixa.getChildren().add(bloco("Mensagem do aviso", msg));

        VBox pessoas = new VBox(4);
        if (l.participantesOuVazio().isEmpty()) {
            pessoas.getChildren().add(new Label("—"));
        } else {
            for (ParticipanteDto p : l.participantesOuVazio()) {
                String det = p.detalhe() == null || p.detalhe().isBlank() ? "" : " — " + p.detalhe();
                Label pl = new Label("• " + p.nome() + det);
                pl.setWrapText(true);
                pessoas.getChildren().add(pl);
            }
        }
        caixa.getChildren().add(bloco("Participantes", pessoas));

        TextField ctx = new TextField(l.contextoMd() == null ? "—" : l.contextoMd());
        ctx.setEditable(false);
        ctx.getStyleClass().add("campo-somente-leitura");
        caixa.getChildren().add(bloco("Contexto (.md)", ctx));

        detalhes.getChildren().setAll(caixa);
    }

    private void atualizarPlaceholder() {
        Estado e = vm.estadoProperty().get();
        Node p;
        if (e == Estado.OFFLINE) {
            p = vazio(Feather.WIFI_OFF, "API fora do ar", DICA_DOCKER, "Tentar de novo");
        } else if (e == Estado.ERRO && vm.todos().isEmpty()) {
            p = vazio(Feather.ALERT_TRIANGLE, "Não foi possível carregar os lembretes", "Veja a mensagem de erro.", "Tentar de novo");
        } else if (e == Estado.CARREGANDO && vm.todos().isEmpty()) {
            ProgressIndicator pi = new ProgressIndicator();
            pi.setMaxSize(40, 40);
            p = pi;
        } else if (vm.todos().isEmpty()) {
            p = vazio(Feather.BELL_OFF, vm.filtroProperty().get() == Filtro.TODOS
                    ? "Nenhum lembrete ainda" : "Nenhum lembrete " + vm.filtroProperty().get().rotulo.toLowerCase().replaceAll("s$", ""),
                    "Clique em Novo (Ctrl+N) para criar.", null);
        } else {
            p = vazio(Feather.SEARCH, "Nada encontrado para essa busca", "Tente outros termos ou limpe a busca (Esc).", null);
        }
        tabela.setPlaceholder(p);
    }

    private Node vazio(Feather icone, String titulo, String texto, String acao) {
        Label t = new Label(titulo);
        t.getStyleClass().add("vazio-titulo");
        Label d = new Label(texto);
        d.getStyleClass().add("dica");
        VBox caixa = new VBox(8, new FontIcon(icone), t, d);
        caixa.getStyleClass().add("vazio");
        caixa.setAlignment(Pos.CENTER);
        if (acao != null) {
            Button b = new Button(acao, new FontIcon(Feather.REFRESH_CW));
            b.setOnAction(e -> recarregar());
            caixa.getChildren().add(b);
        }
        return caixa;
    }

    // ---------------------------------------------------------------- acoes

    private void recarregar() {
        vm.recarregar(erro -> {
            if (!(erro instanceof ApiOfflineException)) tratarErro("Carregar lembretes", erro);
            else notificacoes.erro("API fora do ar. " + DICA_DOCKER);
        });
    }

    private void novo() {
        abrirFormulario(Modo.NOVO, LembreteForm.vazio(), null);
    }

    private void editar(LembreteResponse l) {
        if (l != null) abrirFormulario(Modo.EDITAR, LembreteForm.de(l), l.id());
    }

    private void duplicar(LembreteResponse l) {
        if (l != null) abrirFormulario(Modo.DUPLICAR, LembreteForm.de(l).comTitulo("Cópia de " + l.titulo()), null);
    }

    private void abrirFormulario(Modo modo, LembreteForm form, Long id) {
        LembreteFormDialog dialogo = new LembreteFormDialog(janela(), modo, form,
                (req, ok, falha) -> vm.salvar(id, req, ok, falha));
        dialogo.showAndWait().ifPresent(salvo -> {
            selecionar(salvo);
            String quando = salvo.proximoDisparo() == null ? ""
                    : " Próximo aviso: " + RecorrenciaFormatter.diaMesHora(salvo.proximoDisparo(), AppConfig.FUSO);
            notificacoes.sucesso("Lembrete salvo." + quando);
        });
    }

    private void excluir(LembreteResponse l) {
        if (l == null) return;
        boolean ok = Confirmacao.confirmar(janela(), "Excluir lembrete",
                "Excluir o lembrete \"" + l.titulo() + "\"? Essa ação não pode ser desfeita.", "Excluir", true);
        if (!ok) return;
        vm.excluir(l, () -> notificacoes.sucesso("Lembrete excluído."), erro -> {
            if (erro instanceof ApiException api && api.status() == 404) {
                notificacoes.info("Esse lembrete já tinha sido removido.");
                recarregar();
            } else {
                tratarErro("Excluir", erro);
            }
        });
    }

    private void testar(LembreteResponse l) {
        if (l == null) return;
        boolean ok = Confirmacao.confirmar(janela(), "Testar no Discord",
                "Enviar o aviso de \"" + l.titulo() + "\" agora no Discord?", "Enviar", false);
        if (!ok) return;
        vm.testar(l, texto -> notificacoes.sucesso("Aviso enviado no Discord.", "Ver texto",
                () -> Confirmacao.mostrarTexto(janela(), "Texto enviado", texto)), erro -> {
            if (erro instanceof ApiException api && api.status() == 502) {
                notificacoes.erro("Discord indisponível: " + api.getMessage());
            } else {
                tratarErro("Testar no Discord", erro);
            }
        });
    }

    private void tratarErro(String acao, Throwable erro) {
        LOG.log(Level.WARNING, acao + " falhou", erro);
        if (erro instanceof ApiOfflineException) {
            notificacoes.erro("API fora do ar. " + DICA_DOCKER);
        } else if (erro instanceof ApiException api && api.status() == 404) {
            notificacoes.info("Lembrete não encontrado. A lista foi atualizada.");
            recarregar();
        } else if (erro instanceof ApiException api) {
            notificacoes.erro(acao + ": " + (api.status() > 0 ? "HTTP " + api.status() + " · " : "") + api.getMessage());
        } else {
            notificacoes.erro(acao + ": erro inesperado (" + erro.getClass().getSimpleName() + ")");
        }
    }

    // ---------------------------------------------------------------- utilidades

    private void selecionar(LembreteResponse l) {
        if (tabela.getItems().contains(l)) {
            tabela.getSelectionModel().select(l);
            tabela.scrollTo(l);
        } else {
            mostrarDetalhes(selecionado());
        }
    }

    private LembreteResponse selecionado() {
        return tabela.getSelectionModel().getSelectedItem();
    }

    private Window janela() {
        return raiz.getScene() == null ? null : raiz.getScene().getWindow();
    }

    private static ZonedDateTime agora() {
        return ZonedDateTime.now(AppConfig.FUSO);
    }

    private static String dataComRelativo(OffsetDateTime quando) {
        if (quando == null) return "—";
        return RecorrenciaFormatter.diaMesHora(quando, AppConfig.FUSO) + " · " + RecorrenciaFormatter.relativo(quando, agora());
    }

    private static Button botao(String texto, Feather icone, String dica, javafx.event.EventHandler<javafx.event.ActionEvent> acao) {
        Button b = new Button(texto, new FontIcon(icone));
        b.setTooltip(new Tooltip(dica));
        b.setOnAction(acao);
        return b;
    }

    private static Button botaoIcone(Feather icone, String dica, javafx.event.EventHandler<javafx.event.ActionEvent> acao) {
        Button b = new Button(null, new FontIcon(icone));
        b.getStyleClass().add(Styles.BUTTON_ICON);
        b.setTooltip(new Tooltip(dica));
        b.setOnAction(acao);
        return b;
    }

    private static MenuItem item(String texto, Feather icone, Runnable acao) {
        MenuItem m = new MenuItem(texto, new FontIcon(icone));
        m.setOnAction(e -> acao.run());
        return m;
    }

    private static Label rotulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("rotulo");
        return l;
    }

    private static VBox bloco(String titulo, Node conteudo) {
        return new VBox(4, rotulo(titulo), conteudo);
    }
}
