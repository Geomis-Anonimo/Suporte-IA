package dev.interfacegrafica.tarefas;

import atlantafx.base.theme.Styles;
import dev.interfacegrafica.client.dto.TarefasDtos.Prioridade;
import dev.interfacegrafica.client.dto.TarefasDtos.Status;
import dev.interfacegrafica.client.dto.TarefasDtos.TarefaDto;
import dev.interfacegrafica.config.AppConfig;
import java.time.format.DateTimeFormatter;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Card de uma tarefa no board. Arrastavel; clique seleciona, duplo-clique edita. */
public class CardTarefa extends VBox {
    /** Prefixo do conteudo arrastado (evita aceitar textos quaisquer soltos no board). */
    public static final String PREFIXO_DRAG = "tarefa:";
    private static final PseudoClass SELECIONADO = PseudoClass.getPseudoClass("selecionado");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    private final TarefaDto tarefa;

    public CardTarefa(TarefaDto t, boolean mostrarProjeto, BoardView.Acoes acoes) {
        this.tarefa = t;
        getStyleClass().addAll("card-tarefa", "prioridade-" + t.prioridade().name().toLowerCase());
        if (t.status() == Status.FEITO) getStyleClass().add("card-feito");
        if (t.status() == Status.BLOQUEADO) getStyleClass().add("card-bloqueado");
        setSpacing(6);
        setFocusTraversable(false);

        Label titulo = new Label(t.titulo());
        titulo.getStyleClass().add("titulo-card");
        titulo.setWrapText(true);
        titulo.setMaxHeight(60); // ~3 linhas; o resto vira "..."
        titulo.setMinHeight(Region.USE_PREF_SIZE);
        titulo.setTooltip(t.titulo().length() > 80 ? new Tooltip(t.titulo()) : null);
        getChildren().add(titulo);

        if (t.status() == Status.BLOQUEADO && t.motivoBloqueio() != null) {
            Label motivo = new Label(t.motivoBloqueio(), new FontIcon(Feather.ALERT_OCTAGON));
            motivo.getStyleClass().add("motivo-card");
            motivo.setWrapText(true);
            getChildren().add(motivo);
        }

        FlowPane chips = new FlowPane(4, 4);
        if (mostrarProjeto && t.projetoSlug() != null) chips.getChildren().add(chip(t.projetoSlug(), "chip-projeto"));
        for (String tag : t.tagsOuVazio()) chips.getChildren().add(chip("#" + tag, "chip-tag"));
        if (!chips.getChildren().isEmpty()) getChildren().add(chips);

        HBox rodape = new HBox(6);
        rodape.setAlignment(Pos.CENTER_LEFT);
        FontIcon prio = new FontIcon(switch (t.prioridade()) {
            case ALTA -> Feather.CHEVRONS_UP;
            case MEDIA -> Feather.MINUS;
            case BAIXA -> Feather.CHEVRONS_DOWN;
        });
        prio.getStyleClass().add("icone-prioridade");
        Label prioridade = new Label(t.prioridade() == Prioridade.MEDIA ? "" : t.prioridade().rotulo, prio);
        prioridade.getStyleClass().add("rotulo-prioridade");
        Tooltip.install(prioridade, new Tooltip("Prioridade " + t.prioridade().rotulo.toLowerCase()));
        rodape.getChildren().add(prioridade);
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        rodape.getChildren().add(espaco);
        if (t.status() == Status.FEITO && t.concluidaEm() != null) {
            Label quando = new Label("Concluída " + t.concluidaEm().atZoneSameInstant(AppConfig.FUSO).format(DIA));
            quando.getStyleClass().add("data-card");
            rodape.getChildren().add(quando);
        }
        if (t.link() != null) {
            Button link = new Button(null, new FontIcon(TarefaFormValidator.ehUrl(t.link()) ? Feather.EXTERNAL_LINK : Feather.FILE_TEXT));
            link.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL, "link-card");
            link.setTooltip(new Tooltip("Abrir " + t.link()));
            link.setOnAction(e -> acoes.abrirLink(t));
            rodape.getChildren().add(link);
        }
        getChildren().add(rodape);

        setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            acoes.selecionar(t);
            if (e.getClickCount() == 2) acoes.editar(t);
        });
        setOnContextMenuRequested(e -> {
            acoes.selecionar(t);
            acoes.menu(t).show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        setOnDragDetected(e -> {
            Dragboard db = startDragAndDrop(TransferMode.MOVE);
            ClipboardContent c = new ClipboardContent();
            c.putString(PREFIXO_DRAG + t.id());
            db.setContent(c);
            db.setDragView(snapshot(null, null), e.getX(), e.getY());
            setOpacity(0.35);
            acoes.selecionar(t);
            e.consume();
        });
        setOnDragDone(e -> setOpacity(1));
    }

    public TarefaDto tarefa() { return tarefa; }

    public void setSelecionado(boolean sim) {
        pseudoClassStateChanged(SELECIONADO, sim);
    }

    private static Label chip(String texto, String estilo) {
        Label l = new Label(texto);
        l.getStyleClass().addAll("chip", estilo);
        return l;
    }
}
