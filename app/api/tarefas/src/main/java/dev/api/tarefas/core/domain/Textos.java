package dev.api.tarefas.core.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Utilitarios de texto do dominio (sem dependencias externas). */
public final class Textos {
    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAMINHO_MD = Pattern.compile("^(?!/)(?!.*\\.\\.)[\\w./ -]+\\.md$");

    private Textos() { }

    /** Vazio/branco vira null; senao, sem espacos nas pontas. */
    public static String limpar(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** Minusculas e sem acentos (para busca). */
    public static String normalizar(String s) {
        if (s == null) return "";
        return MARCAS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** Caminho relativo a docs/ terminando em .md, sem "..". */
    public static boolean caminhoDocsValido(String s) {
        return CAMINHO_MD.matcher(s).matches();
    }

    /** URL http(s) ou caminho .md relativo a docs/. */
    public static boolean linkValido(String s) {
        return URL.matcher(s).matches() || caminhoDocsValido(s);
    }
}
