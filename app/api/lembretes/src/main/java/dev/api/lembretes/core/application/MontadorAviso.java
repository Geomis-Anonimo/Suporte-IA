package dev.api.lembretes.core.application;

import dev.api.lembretes.core.domain.Lembrete;
import dev.api.lembretes.core.domain.Participante;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Monta o texto do aviso: cabecalho com o tempo restante + a mensagem em linguagem natural guardada
 * no lembrete (escrita pelo Claude ao cadastrar) + participantes + arquivo de contexto.
 */
public class MontadorAviso {

    public String montar(Lembrete l, Instant agora) {
        long minutos = Math.max(0, Math.round(Duration.between(agora, l.proximaOcorrencia()).toSeconds() / 60.0));
        StringBuilder sb = new StringBuilder();
        sb.append(minutos == 0 ? "⏰ Agora" : "⏰ Daqui " + minutos + " min")
                .append(": **").append(l.titulo()).append("**");

        String corpo = preenchido(l.mensagem()) ? l.mensagem() : l.descricao();
        if (preenchido(corpo)) sb.append("\n").append(corpo.strip());

        String participantes = l.participantes().stream().map(Participante::descricao).collect(Collectors.joining(", "));
        if (!participantes.isBlank()) sb.append("\n👥 Participantes: ").append(participantes);
        if (preenchido(l.contextoMd())) sb.append("\n📄 Contexto: ").append(l.contextoMd());
        return sb.toString();
    }

    private static boolean preenchido(String s) {
        return s != null && !s.isBlank();
    }
}
