package dev.interfacegrafica.tarefas;

import dev.interfacegrafica.client.dto.TarefasDtos.Prioridade;
import dev.interfacegrafica.client.dto.TarefasDtos.Status;
import dev.interfacegrafica.client.dto.TarefasDtos.TarefaRequest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Validacao do formulario de tarefa e conversao para o corpo da api (mesmas regras do servidor). Sem JavaFX. */
public final class TarefaFormValidator {

    public enum Campo { TITULO, SQUAD, MOTIVO, TAGS, LINK }

    /** Valores do formulario como digitados. Record: comparar snapshots diz se ha alteracao nao salva. */
    public record Form(String titulo, Long squadId, Long projetoId, Status status, String motivoBloqueio,
                       Prioridade prioridade, List<String> tags, String link, String descricao) { }

    public static final int MAX_TITULO = 200;
    public static final int MAX_TAG = 30;
    private static final Pattern URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAMINHO_MD = Pattern.compile("^(?!/)(?!.*\\.\\.)[\\w./ -]+\\.md$");

    private TarefaFormValidator() { }

    public static Map<Campo, String> validar(Form f) {
        Map<Campo, String> erros = new EnumMap<>(Campo.class);
        if (vazio(f.titulo())) erros.put(Campo.TITULO, "Informe o título");
        else if (f.titulo().strip().length() > MAX_TITULO) erros.put(Campo.TITULO, "Máximo de " + MAX_TITULO + " caracteres");
        if (f.squadId() == null) erros.put(Campo.SQUAD, "Escolha o squad");
        if (f.status() == Status.BLOQUEADO && vazio(f.motivoBloqueio()))
            erros.put(Campo.MOTIVO, "Diga o que está bloqueando");
        for (String tag : f.tags() == null ? List.<String>of() : f.tags()) {
            String t = tag.strip();
            if (t.length() > MAX_TAG) erros.put(Campo.TAGS, "Tag com mais de " + MAX_TAG + " caracteres: " + t);
            else if (t.chars().anyMatch(Character::isWhitespace)) erros.put(Campo.TAGS, "Tag sem espaços: " + t);
        }
        if (!vazio(f.link()) && !linkValido(f.link().strip()))
            erros.put(Campo.LINK, "Use uma URL http(s) ou um caminho .md relativo a docs/");
        return erros;
    }

    public static boolean linkValido(String link) {
        return URL.matcher(link).matches() || CAMINHO_MD.matcher(link).matches();
    }

    public static boolean ehUrl(String link) {
        return link != null && URL.matcher(link.strip()).matches();
    }

    /** "GMUD, bug  daily" -> [gmud, bug, daily] (sem repetidas). */
    public static List<String> normalizarTags(List<String> tags) {
        List<String> r = new ArrayList<>();
        if (tags == null) return r;
        for (String t : tags) {
            if (t == null || t.isBlank()) continue;
            String tag = t.strip().toLowerCase(Locale.ROOT);
            if (!r.contains(tag)) r.add(tag);
        }
        return r;
    }

    /** So chamar com o formulario valido. */
    public static TarefaRequest paraRequest(Form f) {
        Status status = f.status() == null ? Status.A_FAZER : f.status();
        return new TarefaRequest(f.squadId(), f.projetoId(), f.titulo().strip(), limpar(f.descricao()), status,
                status == Status.BLOQUEADO ? limpar(f.motivoBloqueio()) : null,
                f.prioridade() == null ? Prioridade.MEDIA : f.prioridade(), normalizarTags(f.tags()), limpar(f.link()));
    }

    private static boolean vazio(String s) { return s == null || s.isBlank(); }

    private static String limpar(String s) { return vazio(s) ? null : s.strip(); }
}
