package dev.interfacegrafica.ui;

import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Toasts no canto inferior direito. Sucesso/info somem em 4 s; erros ficam ate serem fechados. */
public class Notificacoes {
    private static final Duration DURACAO = Duration.seconds(4);
    private final VBox camada = new VBox(10);

    public Notificacoes() {
        camada.setAlignment(Pos.BOTTOM_RIGHT);
        camada.setFillWidth(false);
        camada.setPadding(new Insets(20));
        camada.setPickOnBounds(false); // cliques fora dos toasts passam para a tela
        camada.getStyleClass().add("notificacoes");
    }

    public Region camada() { return camada; }

    public void sucesso(String msg) { mostrar(msg, Feather.CHECK_CIRCLE, Styles.SUCCESS, true, null, null); }

    /** Sucesso com um botao de acao (ex.: "Ver texto"). */
    public void sucesso(String msg, String acao, Runnable aoClicar) {
        mostrar(msg, Feather.CHECK_CIRCLE, Styles.SUCCESS, true, acao, aoClicar);
    }

    public void info(String msg) { mostrar(msg, Feather.INFO, Styles.ACCENT, true, null, null); }

    public void erro(String msg) { mostrar(msg, Feather.ALERT_TRIANGLE, Styles.DANGER, false, null, null); }

    private void mostrar(String msg, Feather icone, String estilo, boolean someSozinho, String acao, Runnable aoClicar) {
        Notification n = new Notification(msg, new FontIcon(icone));
        n.getStyleClass().addAll(Styles.ELEVATED_1, estilo);
        n.setPrefWidth(380);
        n.setMaxWidth(380);
        n.setOnClose(e -> camada.getChildren().remove(n));
        if (acao != null) {
            Button b = new Button(acao);
            b.getStyleClass().add(Styles.SMALL);
            b.setOnAction(e -> {
                camada.getChildren().remove(n);
                aoClicar.run();
            });
            n.setPrimaryActions(b);
        }
        camada.getChildren().add(n);
        if (camada.getChildren().size() > 4) camada.getChildren().remove(0);
        if (someSozinho) {
            PauseTransition espera = new PauseTransition(acao != null ? DURACAO.multiply(2) : DURACAO);
            espera.setOnFinished(e -> camada.getChildren().remove(n));
            espera.play();
        }
    }
}
