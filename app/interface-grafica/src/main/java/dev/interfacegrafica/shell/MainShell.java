package dev.interfacegrafica.shell;

import atlantafx.base.theme.Styles;
import dev.interfacegrafica.ui.ApiMonitor;
import dev.interfacegrafica.ui.Logo;
import javafx.scene.image.ImageView;
import dev.interfacegrafica.ui.Notificacoes;
import dev.interfacegrafica.ui.Tema;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Janela principal: menu lateral, barra superior (status da API + tema) e area de conteudo. */
public class MainShell {
    private static final String DICA_OFFLINE = "Rode \"docker compose up -d\" na pasta CLAUDE";

    private final StackPane raiz;
    private final StackPane conteudo = new StackPane();
    private final Label tituloTela = new Label();
    private final ToggleGroup grupo = new ToggleGroup();

    public MainShell(List<Tela> telas, ApiMonitor monitor, Notificacoes notificacoes) {
        BorderPane layout = new BorderPane();
        layout.setLeft(menuLateral(telas));
        layout.setTop(barraSuperior(monitor));
        layout.setCenter(conteudo);
        conteudo.getStyleClass().add("conteudo");
        raiz = new StackPane(layout, notificacoes.camada());
        if (!telas.isEmpty()) mostrar(telas.get(0));
    }

    public Parent raiz() { return raiz; }

    private VBox menuLateral(List<Tela> telas) {
        ImageView marca = new ImageView();
        marca.imageProperty().bind(Logo.atual());
        marca.setFitWidth(44);
        marca.setFitHeight(44);
        marca.setPreserveRatio(true);
        marca.setSmooth(true);
        StackPane marcaBox = new StackPane(marca);
        marcaBox.getStyleClass().add("sidebar-marca");

        VBox menu = new VBox(marcaBox);
        menu.getStyleClass().add("sidebar");
        menu.setAlignment(Pos.TOP_CENTER);
        for (Tela tela : telas) {
            ToggleButton item = new ToggleButton(tela.nome(), new FontIcon(tela.icone()));
            item.setContentDisplay(ContentDisplay.TOP);
            item.getStyleClass().add("sidebar-item");
            item.setToggleGroup(grupo);
            item.setUserData(tela);
            item.setOnAction(e -> mostrar(tela));
            menu.getChildren().add(item);
        }
        // nunca deixar nenhum item selecionado
        grupo.selectedToggleProperty().addListener((obs, antigo, novo) -> {
            if (novo == null && antigo != null) grupo.selectToggle(antigo);
        });
        return menu;
    }

    private HBox barraSuperior(ApiMonitor monitor) {
        tituloTela.getStyleClass().add(Styles.TITLE_3);
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        Circle ponto = new Circle(5);
        ponto.getStyleClass().add("status-ponto");
        Label statusTexto = new Label();
        HBox status = new HBox(ponto, statusTexto);
        status.getStyleClass().add("status-api");
        Tooltip dica = new Tooltip();
        Tooltip.install(status, dica);
        monitor.statusProperty().addListener((obs, a, s) -> atualizarStatus(s, status, statusTexto, dica));
        atualizarStatus(monitor.statusProperty().get(), status, statusTexto, dica);

        Button tema = new Button();
        tema.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        tema.setTooltip(new Tooltip("Alternar tema claro/escuro"));
        Runnable iconeTema = () -> tema.setGraphic(new FontIcon(Tema.escuroProperty().get() ? Feather.SUN : Feather.MOON));
        iconeTema.run();
        Tema.escuroProperty().addListener((obs, a, b) -> iconeTema.run());
        tema.setOnAction(e -> Tema.alternar());

        HBox barra = new HBox(tituloTela, espaco, status, tema);
        barra.getStyleClass().add("barra-superior");
        return barra;
    }

    private static void atualizarStatus(ApiMonitor.Status s, HBox caixa, Label texto, Tooltip dica) {
        caixa.getStyleClass().removeAll("online", "offline");
        switch (s) {
            case ONLINE -> {
                caixa.getStyleClass().add("online");
                texto.setText("API online");
                dica.setText("api respondendo");
            }
            case OFFLINE -> {
                caixa.getStyleClass().add("offline");
                texto.setText("API offline");
                dica.setText(DICA_OFFLINE);
            }
            case VERIFICANDO -> {
                texto.setText("Verificando API…");
                dica.setText("Verificando a api");
            }
        }
    }

    private void mostrar(Tela tela) {
        conteudo.getChildren().setAll(tela.conteudo());
        tituloTela.setText(tela.nome());
        grupo.getToggles().stream().filter(t -> t.getUserData() == tela).findFirst().ifPresent(grupo::selectToggle);
        tela.aoMostrar();
    }
}
