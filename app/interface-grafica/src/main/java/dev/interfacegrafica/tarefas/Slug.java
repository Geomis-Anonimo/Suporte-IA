package dev.interfacegrafica.tarefas;

import dev.interfacegrafica.lembretes.TextoBusca;
import java.util.regex.Pattern;

/** Slugs de squad/projeto (mesma regra da api: minusculas, numeros, '-' e '_'). Sem JavaFX. */
public final class Slug {
    private static final Pattern VALIDO = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,79}$");

    private Slug() { }

    /** "Tela Web" -> "tela-web"; "Ação Nova!" -> "acao-nova". */
    public static String gerar(String nome) {
        String s = TextoBusca.normalizar(nome).trim()
                .replaceAll("[^a-z0-9_\\s-]", "")
                .replaceAll("[\\s-]+", "-")
                .replaceAll("^[-_]+|[-_]+$", "");
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    public static boolean valido(String slug) {
        return slug != null && VALIDO.matcher(slug).matches();
    }
}
