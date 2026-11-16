package dev.interfacegrafica.ui;

import atlantafx.base.theme.Styles;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

/** Dialogos simples de confirmacao e de exibicao de texto. */
public final class Confirmacao {
    private Confirmacao() { }

    /** @param perigo true = botao de confirmar vermelho */
    public static boolean confirmar(Window dono, String titulo, String texto, String textoBotao, boolean perigo) {
        ButtonType ok = new ButtonType(textoBotao, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, texto, cancelar, ok);
        alert.initOwner(dono);
        alert.setTitle(titulo);
        alert.setHeaderText(titulo);
        alert.getDialogPane().getStylesheets().add(Estilos.css());
        alert.getDialogPane().lookupButton(ok).getStyleClass().add(perigo ? Styles.DANGER : Styles.ACCENT);
        alert.getDialogPane().setPrefWidth(460);
        return alert.showAndWait().filter(ok::equals).isPresent();
    }

    /** Mostra um texto longo, selecionavel (para copiar). */
    public static void mostrarTexto(Window dono, String titulo, String texto) {
        TextArea area = new TextArea(texto);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(12);
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.CLOSE);
        alert.initOwner(dono);
        alert.setTitle(titulo);
        alert.setHeaderText(titulo);
        alert.getDialogPane().setContent(area);
        alert.getDialogPane().getStylesheets().add(Estilos.css());
        alert.getDialogPane().setPrefWidth(560);
        alert.setResizable(true);
        alert.showAndWait();
    }
}
