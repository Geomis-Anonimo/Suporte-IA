package dev.api.tarefas.core.domain;

import java.util.regex.Pattern;

/** Identificador legivel de squad/projeto: o mesmo nome da pasta em docs/ (ex.: guardian, tela-web, guardian_whatsapp). */
public final class Slug {
    private static final Pattern VALIDO = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,79}$");

    private Slug() { }

    public static String validar(String slug, String campo) {
        if (slug == null || slug.isBlank()) throw new IllegalArgumentException(campo + " e obrigatorio");
        String s = slug.trim();
        if (!VALIDO.matcher(s).matches())
            throw new IllegalArgumentException(campo + " invalido: use minusculas, numeros, '-' ou '_' (ex.: tela-web)");
        return s;
    }
}
