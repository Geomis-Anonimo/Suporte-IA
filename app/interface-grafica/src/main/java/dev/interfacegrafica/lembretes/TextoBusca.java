package dev.interfacegrafica.lembretes;

import dev.interfacegrafica.client.dto.LembreteResponse;
import dev.interfacegrafica.client.dto.ParticipanteDto;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Busca local: ignora maiusculas e acentos. Sem JavaFX. */
public final class TextoBusca {
    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");

    private TextoBusca() { }

    public static String normalizar(String s) {
        if (s == null) return "";
        return MARCAS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** Todos os termos (separados por espaco) precisam aparecer em algum campo. */
    public static boolean corresponde(LembreteResponse l, String busca) {
        String b = normalizar(busca).trim();
        if (b.isEmpty()) return true;
        StringBuilder alvo = new StringBuilder()
                .append(l.titulo()).append(' ')
                .append(l.descricao()).append(' ')
                .append(l.mensagem()).append(' ')
                .append(l.contextoMd()).append(' ');
        for (ParticipanteDto p : l.participantesOuVazio()) alvo.append(p.nome()).append(' ').append(p.detalhe()).append(' ');
        String texto = normalizar(alvo.toString());
        for (String termo : b.split("\\s+")) {
            if (!texto.contains(termo)) return false;
        }
        return true;
    }
}
