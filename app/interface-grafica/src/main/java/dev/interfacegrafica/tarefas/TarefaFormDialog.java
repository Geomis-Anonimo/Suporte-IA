package dev.interfacegrafica.tarefas;

import atlantafx.base.theme.Styles;
import dev.interfacegrafica.client.dto.TarefasDtos.Prioridade;
import dev.interfacegrafica.client.dto.TarefasDtos.ProjetoDto;
import dev.interfacegrafica.client.dto.TarefasDtos.SquadDto;
import dev.interfacegrafica.client.dto.TarefasDtos.Status;
import dev.interfacegrafica.client.dto.TarefasDtos.TarefaDto;
import dev.interfacegrafica.client.dto.TarefasDtos.TarefaRequest;
import dev.interfacegrafica.config.AppConfig;
import dev.interfacegrafica.tarefas.TarefaFormValidator.Campo;
import dev.interfacegrafica.tarefas.TarefaFormValidator.Form;
import dev.interfacegrafica.ui.Confirmacao;
import dev.interfacegrafica.ui.Estilos;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Formulario de tarefa (nova / editar / duplicar). Salva de forma assincrona e so fecha no sucesso. */
public class TarefaFormDialog extends Dialog<TarefaDto> {

    public enum Modo {
        NOVA("Nova tarefa"), EDITAR("Editar tarefa"), DUPLICAR("Duplicar tarefa");

        final String titulo;

        Modo(String titulo) { this.titulo = titulo; }
    }

    @FunctionalInterface
    public interface Salvar {
        void executar(TarefaRequest req, Consumer<TarefaDto> ok, Consumer<Throwable> falha);
    }

    /** Opcao do combo de projeto; {@code projeto} nulo = "Sem projeto". */
    private record OpcaoProjeto(ProjetoDto projeto) {
        Long id() { return projeto == null ? null : projeto.id(); }

        @Override public String toString() { return projeto == null ? "Sem projeto" : projeto.nome(); }
    }

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TextField titulo = new TextField();
    private final ComboBox<SquadDto> squad = new ComboBox<>();
    private final ComboBox<OpcaoProjeto> projeto = new ComboBox<>();
    private final ToggleGroup grupoStatus = new ToggleGroup();
    private final TextField motivo = new TextField();
    private final VBox caixaMotivo;
    private final ToggleGroup grupoPrioridade = new ToggleGroup();
    private final FlowPane chipsTags = new FlowPane(6, 6);
    private final List<String> tags = new ArrayList<>();
    private final TextField novaTag = new TextField();
    private final ContextMenu sugestoes = new ContextMenu();
    private final List<String> tagsConhecidas;
    private final TextField link = new TextField();
    private final TextArea descricao = new TextArea();
    private final Label bannerErro = new Label();

    private final List<ProjetoDto> todosProjetos;
    private final Map<Campo, Control> controles = new EnumMap<>(Campo.class);
    private final Map<Campo, Label> erros = new EnumMap<>(Campo.class);
    private final Set<Campo> tocados = EnumSet.noneOf(Campo.class);
    private final BooleanProperty valido = new SimpleBooleanProperty(false);
    private final BooleanProperty salvando = new SimpleBooleanProperty(false);
    private final Button botaoSalvar;
    private final Form inicial;
    private boolean concluido;
    private boolean preenchendo;

    /**
     * @param base       tarefa a editar/duplicar, ou null (nova)
     * @param squadId    squad pre-selecionado (nova)
     * @param projetoId  projeto pre-selecionado (nova); null = sem projeto
     * @param abrirLink  acao do botao "Abrir" ao lado do link
     */
    public TarefaFormDialog(Window dono, Modo modo, TarefaDto base, Long squadId, Long projetoId, Status statusInicial,
                            List<SquadDto> squads, List<ProjetoDto> projetos, List<String> tagsConhecidas,
                            Consumer<String> abrirLink, Salvar salvar) {
        this.todosProjetos = projetos;
        this.tagsConhecidas = tagsConhecidas;
        initOwner(dono);
        setTitle(modo.titulo);
        setHeaderText(modo.titulo);
        setResizable(true);

        DialogPane pane = getDialogPane();
        pane.getStylesheets().add(Estilos.css());
        pane.getStyleClass().add("form-lembrete");
        ButtonType tipoSalvar = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        ButtonType tipoCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(tipoCancelar, tipoSalvar);

        squad.getItems().setAll(squads.stream().filter(s -> !s.arquivado() || (base != null && s.id().equals(base.squadId()))).toList());
        squad.setCellFactory(l -> celulaSquad());
        squad.setButtonCell(celulaSquad());
        squad.setMaxWidth(Double.MAX_VALUE);
        projeto.setMaxWidth(Double.MAX_VALUE);

        motivo.setPromptText("Ex.: aguardando acesso do DevOps (Ruan)");
        caixaMotivo = campo("O que está bloqueando? *", motivo, Campo.MOTIVO);

        VBox corpo = montarCorpo(abrirLink);
        corpo.disableProperty().bind(salvando);
        ScrollPane rolagem = new ScrollPane(corpo);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("edge-to-edge");
        rolagem.setPrefViewportHeight(560);
        pane.setContent(rolagem);
        pane.setPrefWidth(640);

        preencher(base, modo, squadId, projetoId, statusInicial);
        if (base != null && modo == Modo.EDITAR) corpo.getChildren().add(metadados(base));
        inicial = ler();
        if (modo != Modo.NOVA) tocados.addAll(EnumSet.allOf(Campo.class));
        ligarValidacao();
        revalidar();

        botaoSalvar = (Button) pane.lookupButton(tipoSalvar);
        botaoSalvar.getStyleClass().add(Styles.ACCENT);
        botaoSalvar.disableProperty().bind(valido.not().or(salvando));
        botaoSalvar.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            enviar(salvar);
        });

        setResultConverter(b -> null);
        setOnCloseRequest(e -> {
            if (concluido) return;
            if (salvando.get()) {
                e.consume();
                return;
            }
            if (!ler().equals(inicial) && !Confirmacao.confirmar(pane.getScene().getWindow(), "Descartar alterações?",
                    "Há alterações não salvas. Fechar mesmo assim?", "Descartar", true)) {
                e.consume();
            }
        });
        setOnShown(e -> Platform.runLater(titulo::requestFocus));
    }

    // ------------------------------------------------------------- layout

    private VBox montarCorpo(Consumer<String> abrirLink) {
        bannerErro.getStyleClass().add("banner-erro");
        bannerErro.setWrapText(true);
        bannerErro.setMaxWidth(Double.MAX_VALUE);
        mostrar(bannerErro, false);

        titulo.setPromptText("Ex.: Validar template de cobrança na Omne");

        HBox linhaOnde = new HBox(16, campo("Squad *", squad, Campo.SQUAD), campo("Projeto", projeto, null));
        linhaOnde.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));
        squad.valueProperty().addListener((obs, a, s) -> {
            Long manter = projeto.getValue() == null ? null : projeto.getValue().id();
            carregarProjetos(s, manter);
        });

        HBox pilulasStatus = pilulas(grupoStatus, Status.values(), s -> ((Status) s).rotulo);
        HBox pilulasPrioridade = pilulas(grupoPrioridade, Prioridade.values(), p -> ((Prioridade) p).rotulo);
        grupoStatus.selectedToggleProperty().addListener((obs, a, b) -> mostrar(caixaMotivo, statusAtual() == Status.BLOQUEADO));

        // tags: chips + campo com autocomplete
        novaTag.setPromptText("Adicionar tag (Enter ou vírgula)");
        novaTag.setPrefColumnCount(18);
        novaTag.textProperty().addListener((obs, a, b) -> {
            if (b.contains(",")) {
                for (String parte : b.split(",")) adicionarTag(parte);
                novaTag.clear();
            } else {
                mostrarSugestoes(b);
            }
        });
        novaTag.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                adicionarTag(novaTag.getText());
                novaTag.clear();
                e.consume();
            } else if (e.getCode() == KeyCode.BACK_SPACE && novaTag.getText().isEmpty() && !tags.isEmpty()) {
                removerTag(tags.get(tags.size() - 1));
            }
        });
        Label erroTags = erroLabel();
        erros.put(Campo.TAGS, erroTags);
        controles.put(Campo.TAGS, novaTag);
        VBox caixaTags = new VBox(4, rotulo("Tags"), new VBox(6, chipsTags, novaTag), erroTags);

        link.setPromptText("https://...  ou  guardian/dailies/2026-11-16-daily.md");
        Button abrir = new Button("Abrir", new FontIcon(Feather.EXTERNAL_LINK));
        abrir.disableProperty().bind(link.textProperty().isEmpty());
        abrir.setOnAction(e -> abrirLink.accept(link.getText()));
        HBox linhaLink = new HBox(8, link, abrir);
        HBox.setHgrow(link, Priority.ALWAYS);
        Label erroLink = erroLabel();
        erros.put(Campo.LINK, erroLink);
        controles.put(Campo.LINK, link);
        VBox caixaLink = new VBox(4, rotulo("Link"), linhaLink, erroLink,
                dica("URL (PR, card do Azure) ou caminho da ata/daily relativo a docs/."));

        descricao.setWrapText(true);
        descricao.setPrefRowCount(5);
        descricao.setPromptText("Detalhes, contexto, critérios de pronto…");

        VBox corpo = new VBox(14, bannerErro,
                campo("Título *", titulo, Campo.TITULO),
                linhaOnde,
                new VBox(4, rotulo("Status"), pilulasStatus),
                caixaMotivo,
                new VBox(4, rotulo("Prioridade"), pilulasPrioridade),
                caixaTags,
                caixaLink,
                new VBox(4, rotulo("Descrição"), descricao));
        corpo.getStyleClass().add("form-corpo");
        return corpo;
    }

    private Node metadados(TarefaDto t) {
        Label l = new Label("Criada em " + data(t.criadaEm()) + "  ·  Atualizada em " + data(t.atualizadaEm())
                + (t.concluidaEm() == null ? "" : "  ·  Concluída em " + data(t.concluidaEm())));
        l.getStyleClass().add("dica");
        l.setWrapText(true);
        return l;
    }

    private static String data(OffsetDateTime d) {
        return d == null ? "—" : d.atZoneSameInstant(AppConfig.FUSO).format(DATA_HORA);
    }

    private <E> HBox pilulas(ToggleGroup grupo, E[] valores, java.util.function.Function<Object, String> rotulo) {
        HBox caixa = new HBox();
        for (int i = 0; i < valores.length; i++) {
            ToggleButton b = new ToggleButton(rotulo.apply(valores[i]));
            b.getStyleClass().add(i == 0 ? Styles.LEFT_PILL : i == valores.length - 1 ? Styles.RIGHT_PILL : Styles.CENTER_PILL);
            b.setUserData(valores[i]);
            b.setToggleGroup(grupo);
            caixa.getChildren().add(b);
        }
        grupo.selectedToggleProperty().addListener((obs, antigo, novo) -> {
            if (novo == null && antigo != null) grupo.selectToggle(antigo);
        });
        return caixa;
    }

    private void carregarProjetos(SquadDto s, Long manterId) {
        List<OpcaoProjeto> opcoes = new ArrayList<>();
        opcoes.add(new OpcaoProjeto(null));
        if (s != null) {
            todosProjetos.stream().filter(p -> p.squadId() == s.id() && (!p.arquivado() || Objects.equals(p.id(), manterId)))
                    .map(OpcaoProjeto::new).forEach(opcoes::add);
        }
        projeto.getItems().setAll(opcoes);
        projeto.setValue(opcoes.stream().filter(o -> Objects.equals(o.id(), manterId)).findFirst().orElse(opcoes.get(0)));
    }

    // ------------------------------------------------------------- tags

    private void adicionarTag(String texto) {
        for (String t : TarefaFormValidator.normalizarTags(List.of(texto == null ? "" : texto))) {
            if (!tags.contains(t)) tags.add(t);
        }
        sugestoes.hide();
        desenharTags();
    }

    private void removerTag(String tag) {
        tags.remove(tag);
        desenharTags();
    }

    private void desenharTags() {
        chipsTags.getChildren().clear();
        for (String t : tags) {
            Button x = new Button(null, new FontIcon(Feather.X));
            x.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            x.setOnAction(e -> removerTag(t));
            Label chip = new Label("#" + t, x);
            chip.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
            chip.getStyleClass().addAll("chip", "chip-tag", "chip-editavel");
            chipsTags.getChildren().add(chip);
        }
        if (!preenchendo) {
            tocados.add(Campo.TAGS);
            revalidar();
        }
    }

    private void mostrarSugestoes(String prefixo) {
        String p = prefixo == null ? "" : prefixo.strip().toLowerCase();
        if (p.isEmpty()) {
            sugestoes.hide();
            return;
        }
        List<CustomMenuItem> itens = tagsConhecidas.stream().filter(t -> t.startsWith(p) && !tags.contains(t)).limit(8)
                .map(t -> {
                    CustomMenuItem item = new CustomMenuItem(new Label("#" + t), true);
                    item.setOnAction(e -> {
                        adicionarTag(t);
                        novaTag.clear();
                    });
                    return item;
                }).toList();
        if (itens.isEmpty()) {
            sugestoes.hide();
            return;
        }
        sugestoes.getItems().setAll(itens);
        if (!sugestoes.isShowing()) sugestoes.show(novaTag, Side.BOTTOM, 0, 0);
    }

    // ------------------------------------------------------------- dados

    private void preencher(TarefaDto base, Modo modo, Long squadId, Long projetoId, Status statusInicial) {
        preenchendo = true;
        Long sId = base != null ? base.squadId() : squadId;
        Long pId = base != null ? base.projetoId() : projetoId;
        squad.setValue(squad.getItems().stream().filter(s -> s.id().equals(sId)).findFirst().orElse(null));
        carregarProjetos(squad.getValue(), pId);
        titulo.setText(base == null ? "" : modo == Modo.DUPLICAR ? "Cópia de " + base.titulo() : base.titulo());
        Status st = base == null ? (statusInicial == null ? Status.A_FAZER : statusInicial)
                : modo == Modo.DUPLICAR ? Status.A_FAZER : base.status();
        selecionar(grupoStatus, st);
        motivo.setText(base == null || base.motivoBloqueio() == null ? "" : base.motivoBloqueio());
        mostrar(caixaMotivo, st == Status.BLOQUEADO);
        selecionar(grupoPrioridade, base == null ? Prioridade.MEDIA : base.prioridade());
        tags.clear();
        if (base != null) tags.addAll(base.tagsOuVazio());
        desenharTags();
        link.setText(base == null || base.link() == null ? "" : base.link());
        descricao.setText(base == null || base.descricao() == null ? "" : base.descricao());
        preenchendo = false;
    }

    private static void selecionar(ToggleGroup g, Object valor) {
        g.getToggles().stream().filter(t -> t.getUserData() == valor).findFirst().ifPresent(g::selectToggle);
    }

    private Status statusAtual() {
        return grupoStatus.getSelectedToggle() == null ? Status.A_FAZER : (Status) grupoStatus.getSelectedToggle().getUserData();
    }

    private Form ler() {
        Prioridade prio = grupoPrioridade.getSelectedToggle() == null ? Prioridade.MEDIA
                : (Prioridade) grupoPrioridade.getSelectedToggle().getUserData();
        return new Form(titulo.getText(), squad.getValue() == null ? null : squad.getValue().id(),
                projeto.getValue() == null ? null : projeto.getValue().id(), statusAtual(), motivo.getText(), prio,
                List.copyOf(tags), link.getText(), descricao.getText());
    }

    // ------------------------------------------------------------- validacao

    private void ligarValidacao() {
        titulo.textProperty().addListener((o, a, b) -> tocarERevalidar(Campo.TITULO));
        squad.valueProperty().addListener((o, a, b) -> tocarERevalidar(Campo.SQUAD));
        motivo.textProperty().addListener((o, a, b) -> tocarERevalidar(Campo.MOTIVO));
        link.textProperty().addListener((o, a, b) -> tocarERevalidar(Campo.LINK));
        grupoStatus.selectedToggleProperty().addListener((o, a, b) -> revalidar());
        controles.forEach((c, ctl) -> ctl.focusedProperty().addListener((o, a, foco) -> {
            if (!foco) tocarERevalidar(c);
        }));
    }

    private void tocarERevalidar(Campo c) {
        if (!preenchendo) tocados.add(c);
        revalidar();
    }

    private void revalidar() {
        if (preenchendo) return;
        Map<Campo, String> encontrados = TarefaFormValidator.validar(ler());
        valido.set(encontrados.isEmpty());
        for (Campo c : erros.keySet()) {
            String msg = tocados.contains(c) ? encontrados.get(c) : null;
            Label l = erros.get(c);
            l.setText(msg == null ? "" : msg);
            mostrar(l, msg != null);
            Control ctl = controles.get(c);
            if (ctl != null) ctl.pseudoClassStateChanged(Styles.STATE_DANGER, msg != null);
        }
    }

    // ------------------------------------------------------------- salvar

    private void enviar(Salvar salvar) {
        if (!novaTag.getText().isBlank()) {
            adicionarTag(novaTag.getText());
            novaTag.clear();
        }
        tocados.addAll(EnumSet.allOf(Campo.class));
        revalidar();
        if (!valido.get()) return;
        TarefaRequest req = TarefaFormValidator.paraRequest(ler());
        mostrar(bannerErro, false);
        salvando.set(true);
        botaoSalvar.setText("Salvando…");
        salvar.executar(req, salva -> {
            salvando.set(false);
            concluido = true;
            setResult(salva);
            close();
        }, erro -> {
            salvando.set(false);
            botaoSalvar.setText("Salvar");
            bannerErro.setText(Erros.mensagem(erro));
            mostrar(bannerErro, true);
        });
    }

    // ------------------------------------------------------------- utilidades

    private VBox campo(String rotulo, Control controle, Campo campo) {
        VBox caixa = new VBox(4, rotulo(rotulo), controle);
        if (controle instanceof TextField tf) tf.setMaxWidth(Double.MAX_VALUE);
        if (campo != null) {
            Label erro = erroLabel();
            caixa.getChildren().add(erro);
            controles.put(campo, controle);
            erros.put(campo, erro);
        }
        return caixa;
    }

    private static Label erroLabel() {
        Label erro = new Label();
        erro.getStyleClass().add("erro-campo");
        erro.setWrapText(true);
        mostrar(erro, false);
        return erro;
    }

    private static ListCell<SquadDto> celulaSquad() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SquadDto s, boolean vazio) {
                super.updateItem(s, vazio);
                setText(vazio || s == null ? null : s.nome() + (s.arquivado() ? " (arquivado)" : ""));
            }
        };
    }

    private static Label rotulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("rotulo");
        return l;
    }

    private static Label dica(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("dica");
        l.setWrapText(true);
        return l;
    }

    private static void mostrar(Node n, boolean visivel) {
        n.setVisible(visivel);
        n.setManaged(visivel);
    }
}
