package dev.interfacegrafica.tarefas;

import atlantafx.base.theme.Styles;
import dev.interfacegrafica.client.dto.TarefasDtos.ProjetoDto;
import dev.interfacegrafica.client.dto.TarefasDtos.ProjetoRequest;
import dev.interfacegrafica.client.dto.TarefasDtos.SquadDto;
import dev.interfacegrafica.client.dto.TarefasDtos.SquadRequest;
import dev.interfacegrafica.ui.Estilos;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Dialogos pequenos: squad, projeto e motivo do bloqueio. */
final class CadastroDialogs {
    private CadastroDialogs() { }

    /** Quem salva de fato: recebe o corpo e callbacks de sucesso/erro. */
    @FunctionalInterface
    interface Salvar<R, T> {
        void executar(R req, Consumer<T> ok, Consumer<Throwable> falha);
    }

    // ------------------------------------------------------------- squad

    static Optional<SquadDto> squad(Window dono, SquadDto base, Salvar<SquadRequest, SquadDto> salvar) {
        TextField nome = new TextField(base == null ? "" : base.nome());
        nome.setPromptText("Ex.: Tela Web");
        TextField slug = new TextField(base == null ? "" : base.slug());
        slug.setPromptText("tela-web (mesmo nome da pasta em docs/)");
        TextField ordem = new TextField(base == null ? "" : String.valueOf(base.ordem()));
        ordem.setPromptText("no fim");
        ordem.setTextFormatter(new javafx.scene.control.TextFormatter<String>(c -> c.getControlNewText().matches("\\d{0,4}") ? c : null));
        CheckBox arquivado = new CheckBox("Arquivado (some da lista e não aceita tarefas novas)");
        arquivado.setSelected(base != null && base.arquivado());
        sincronizarSlug(nome, slug, base == null);

        BooleanBinding invalido = Bindings.createBooleanBinding(
                () -> nome.getText().isBlank() || !Slug.valido(slug.getText().strip()), nome.textProperty(), slug.textProperty());
        Label erroSlug = erroSlug(slug);

        VBox corpo = new VBox(12, campo("Nome *", nome), campo("Slug *", slug), erroSlug, campo("Ordem no menu", ordem));
        if (base != null) corpo.getChildren().add(arquivado);
        return mostrar(dono, base == null ? "Novo squad" : "Editar squad", corpo, invalido, nome,
                () -> new SquadRequest(slug.getText().strip(), nome.getText().strip(),
                        ordem.getText().isBlank() ? null : Integer.parseInt(ordem.getText()), arquivado.isSelected()),
                salvar);
    }

    // ------------------------------------------------------------- projeto

    static Optional<ProjetoDto> projeto(Window dono, ProjetoDto base, SquadDto squadInicial, List<SquadDto> squads,
                                        Salvar<ProjetoRequest, ProjetoDto> salvar) {
        ComboBox<SquadDto> squad = new ComboBox<>();
        squad.getItems().setAll(squads);
        squad.setCellFactory(l -> celula());
        squad.setButtonCell(celula());
        squad.setMaxWidth(Double.MAX_VALUE);
        Long idInicial = base != null ? Long.valueOf(base.squadId()) : squadInicial == null ? null : squadInicial.id();
        squad.setValue(squads.stream().filter(s -> s.id().equals(idInicial)).findFirst().orElse(null));
        TextField nome = new TextField(base == null ? "" : base.nome());
        nome.setPromptText("Ex.: guardian_whatsapp");
        TextField slug = new TextField(base == null ? "" : base.slug());
        slug.setPromptText("nome da pasta em docs/<squad>/projects/");
        TextField contexto = new TextField(base == null || base.contextoMd() == null ? "" : base.contextoMd());
        contexto.setPromptText("<squad>/projects/<slug>/README.md");
        TextArea descricao = new TextArea(base == null || base.descricao() == null ? "" : base.descricao());
        descricao.setPrefRowCount(3);
        descricao.setWrapText(true);
        CheckBox arquivado = new CheckBox("Arquivado (some da lista e não aceita tarefas novas)");
        arquivado.setSelected(base != null && base.arquivado());
        sincronizarSlug(nome, slug, base == null);

        // sugere o contextoMd enquanto o usuario nao digitar um proprio
        boolean[] contextoManual = {base != null && base.contextoMd() != null};
        Runnable sugerir = () -> {
            if (contextoManual[0] || squad.getValue() == null || slug.getText().isBlank()) return;
            contexto.setText(squad.getValue().slug() + "/projects/" + slug.getText().strip() + "/README.md");
        };
        slug.textProperty().addListener((o, a, b) -> sugerir.run());
        squad.valueProperty().addListener((o, a, b) -> sugerir.run());
        contexto.setOnKeyTyped(e -> contextoManual[0] = true);

        BooleanBinding invalido = Bindings.createBooleanBinding(
                () -> squad.getValue() == null || nome.getText().isBlank() || !Slug.valido(slug.getText().strip())
                        || (!contexto.getText().isBlank() && !contexto.getText().strip().endsWith(".md")),
                squad.valueProperty(), nome.textProperty(), slug.textProperty(), contexto.textProperty());

        VBox corpo = new VBox(12, campo("Squad *", squad), campo("Nome *", nome), campo("Slug *", slug), erroSlug(slug),
                campo("Contexto (.md)", contexto), campo("Descrição", descricao));
        if (base != null) corpo.getChildren().add(arquivado);
        return mostrar(dono, base == null ? "Novo projeto" : "Editar projeto", corpo, invalido, nome,
                () -> new ProjetoRequest(squad.getValue().id(), slug.getText().strip(), nome.getText().strip(),
                        descricao.getText().isBlank() ? null : descricao.getText().strip(),
                        contexto.getText().isBlank() ? null : contexto.getText().strip(), arquivado.isSelected()),
                salvar);
    }

    // ------------------------------------------------------------- motivo do bloqueio

    /** Pergunta o motivo (obrigatorio). Vazio = cancelou. */
    static Optional<String> motivoBloqueio(Window dono, String titulo, String atual) {
        TextField campo = new TextField(atual == null ? "" : atual);
        campo.setPromptText("Ex.: aguardando acesso do DevOps (Ruan)");
        Dialog<String> d = new Dialog<>();
        d.initOwner(dono);
        d.setTitle("Bloqueado");
        d.setHeaderText("O que está bloqueando \"" + titulo + "\"?");
        DialogPane pane = d.getDialogPane();
        pane.getStylesheets().add(Estilos.css());
        ButtonType ok = new ButtonType("Bloquear", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE), ok);
        pane.setContent(new VBox(8, campo));
        pane.setPrefWidth(480);
        Node botao = pane.lookupButton(ok);
        botao.getStyleClass().add(Styles.DANGER);
        botao.disableProperty().bind(campo.textProperty().isEmpty());
        d.setResultConverter(b -> b == ok ? campo.getText().strip() : null);
        d.setOnShown(e -> Platform.runLater(campo::requestFocus));
        return d.showAndWait().filter(s -> !s.isBlank());
    }

    // ------------------------------------------------------------- base

    private static <R, T> Optional<T> mostrar(Window dono, String titulo, VBox corpo, BooleanBinding invalido, Node foco,
                                              Supplier<R> montar, Salvar<R, T> salvar) {
        Dialog<T> d = new Dialog<>();
        d.initOwner(dono);
        d.setTitle(titulo);
        d.setHeaderText(titulo);
        DialogPane pane = d.getDialogPane();
        pane.getStylesheets().add(Estilos.css());
        ButtonType ok = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE), ok);
        Label banner = new Label();
        banner.getStyleClass().add("banner-erro");
        banner.setWrapText(true);
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setVisible(false);
        banner.setManaged(false);
        corpo.getChildren().add(0, banner);
        corpo.getStyleClass().add("form-corpo");
        pane.setContent(corpo);
        pane.setPrefWidth(520);

        BooleanProperty salvando = new SimpleBooleanProperty(false);
        corpo.disableProperty().bind(salvando);
        Button botao = (Button) pane.lookupButton(ok);
        botao.getStyleClass().add(Styles.ACCENT);
        botao.disableProperty().bind(invalido.or(salvando));
        boolean[] concluido = {false};
        botao.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            salvando.set(true);
            banner.setVisible(false);
            banner.setManaged(false);
            salvar.executar(montar.get(), r -> {
                salvando.set(false);
                concluido[0] = true;
                d.setResult(r);
                d.close();
            }, erro -> {
                salvando.set(false);
                banner.setText(Erros.mensagem(erro));
                banner.setVisible(true);
                banner.setManaged(true);
            });
        });
        d.setResultConverter(b -> null);
        d.setOnCloseRequest(e -> {
            if (!concluido[0] && salvando.get()) e.consume();
        });
        d.setOnShown(e -> Platform.runLater(foco::requestFocus));
        return d.showAndWait();
    }

    /** Enquanto o slug nao for editado a mao, ele acompanha o nome. */
    private static void sincronizarSlug(TextField nome, TextField slug, boolean automatico) {
        boolean[] manual = {!automatico};
        nome.textProperty().addListener((o, a, b) -> {
            if (!manual[0]) slug.setText(Slug.gerar(b));
        });
        slug.setOnKeyTyped(e -> manual[0] = true);
    }

    private static Label erroSlug(TextField slug) {
        Label l = new Label("Use minúsculas, números, '-' ou '_' (ex.: tela-web, guardian_whatsapp)");
        l.getStyleClass().add("erro-campo");
        l.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> !slug.getText().isBlank() && !Slug.valido(slug.getText().strip()), slug.textProperty()));
        l.managedProperty().bind(l.visibleProperty());
        return l;
    }

    private static VBox campo(String rotulo, Node controle) {
        Label l = new Label(rotulo);
        l.getStyleClass().add("rotulo");
        if (controle instanceof TextField tf) tf.setMaxWidth(Double.MAX_VALUE);
        return new VBox(4, l, controle);
    }

    private static ListCell<SquadDto> celula() {
        return new ListCell<>() {
            @Override
            protected void updateItem(SquadDto s, boolean vazio) {
                super.updateItem(s, vazio);
                setText(vazio || s == null ? null : s.nome());
            }
        };
    }
}
