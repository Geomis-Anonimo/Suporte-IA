package dev.interfacegrafica.ui;

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.scene.image.Image;

/** Logo do app: favicon-light.png no tema claro, favicon-dark.png no escuro. */
public final class Logo {
    private static final Image CLARO = carregar("favicon-dark.png");
    private static final Image ESCURO = carregar("favicon-light.png");
    private static final ObjectBinding<Image> ATUAL =
            Bindings.createObjectBinding(() -> Tema.escuroProperty().get() ? ESCURO : CLARO, Tema.escuroProperty());

    private Logo() { }

    /** Logo do tema atual; muda sozinha quando o tema muda. */
    public static ObjectBinding<Image> atual() { return ATUAL; }

    private static Image carregar(String nome) {
        return new Image(Objects.requireNonNull(Logo.class.getResource("/dev/interfacegrafica/" + nome)).toExternalForm());
    }
}
