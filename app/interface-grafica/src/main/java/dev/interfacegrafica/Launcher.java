package dev.interfacegrafica;

/**
 * Ponto de entrada do executavel (jpackage). Precisa ser uma classe que NAO estende Application:
 * com o JavaFX no classpath (e nao como modulo), chamar App direto da o erro
 * "JavaFX runtime components are missing".
 */
public final class Launcher {
    private Launcher() { }

    public static void main(String[] args) {
        App.main(args);
    }
}
