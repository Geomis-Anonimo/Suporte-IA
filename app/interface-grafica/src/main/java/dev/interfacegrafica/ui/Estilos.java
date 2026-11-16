package dev.interfacegrafica.ui;

import java.util.Objects;

/** Caminho do CSS do app (aplicado na cena principal e em cada dialogo). */
public final class Estilos {
    private Estilos() { }

    public static String css() {
        return Objects.requireNonNull(Estilos.class.getResource("/dev/interfacegrafica/app.css")).toExternalForm();
    }
}
