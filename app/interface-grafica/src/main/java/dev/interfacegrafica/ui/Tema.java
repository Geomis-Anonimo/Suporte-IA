package dev.interfacegrafica.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/** Tema claro/escuro (AtlantaFX Primer). A escolha fica salva em java.util.prefs. */
public final class Tema {
    private static final Logger LOG = Logger.getLogger(Tema.class.getName());
    private static final String CHAVE = "tema.escuro";
    private static final Preferences PREFS = Preferences.userRoot().node("dev/interfacegrafica");
    private static final BooleanProperty ESCURO = new SimpleBooleanProperty(PREFS.getBoolean(CHAVE, false));

    private Tema() { }

    public static BooleanProperty escuroProperty() { return ESCURO; }

    public static void aplicar() {
        Application.setUserAgentStylesheet(ESCURO.get()
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
    }

    public static void alternar() {
        ESCURO.set(!ESCURO.get());
        aplicar();
        try {
            PREFS.putBoolean(CHAVE, ESCURO.get());
            PREFS.flush();
        } catch (BackingStoreException | RuntimeException e) {
            LOG.log(Level.WARNING, "Nao foi possivel salvar a preferencia de tema", e);
        }
    }
}
