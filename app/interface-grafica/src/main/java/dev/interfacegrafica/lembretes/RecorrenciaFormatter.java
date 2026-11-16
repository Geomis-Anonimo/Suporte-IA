package dev.interfacegrafica.lembretes;

import dev.interfacegrafica.client.dto.RecorrenciaDto;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Textos legiveis para recorrencia e datas (pt-BR). Sem JavaFX. */
public final class RecorrenciaFormatter {
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DIA_MES_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private RecorrenciaFormatter() { }

    /** Como {@link #formatar(RecorrenciaDto, int)} considerando o ano atual. */
    public static String formatar(RecorrenciaDto r) {
        return formatar(r, java.time.Year.now().getValue());
    }

    /**
     * Ex.: "Único · 20/11 15:00" (o ano so aparece se for diferente de {@code anoAtual}: "Único · 05/01/2027 15:00"),
     * "Todo dia · 09:00", "Toda quinta · 09:00", "Todo dia 15 · 09:00".
     */
    public static String formatar(RecorrenciaDto r, int anoAtual) {
        if (r == null || r.tipo() == null) return "—";
        return switch (r.tipo()) {
            case UNICA -> r.dataHora() == null ? "Único" : "Único · "
                    + r.dataHora().format(r.dataHora().getYear() == anoAtual ? DIA_MES_HORA : DATA_HORA);
            case DIARIA -> "Todo dia · " + hora(r);
            case SEMANAL -> r.diaSemana() == null ? "Semanal · " + hora(r) : todo(r.diaSemana()) + " · " + hora(r);
            case MENSAL -> "Todo dia " + (r.diaMes() == null ? "?" : r.diaMes()) + " · " + hora(r);
        };
    }

    /** "segunda", "terça", ... "domingo". */
    public static String nomeDia(DayOfWeek dia) {
        return switch (dia) {
            case MONDAY -> "segunda";
            case TUESDAY -> "terça";
            case WEDNESDAY -> "quarta";
            case THURSDAY -> "quinta";
            case FRIDAY -> "sexta";
            case SATURDAY -> "sábado";
            case SUNDAY -> "domingo";
        };
    }

    /** "Segunda-feira", ..., "Sábado", "Domingo" (para combos). */
    public static String nomeDiaCompleto(DayOfWeek dia) {
        String n = nomeDia(dia);
        String cap = Character.toUpperCase(n.charAt(0)) + n.substring(1);
        return dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY ? cap : cap + "-feira";
    }

    private static String todo(DayOfWeek dia) {
        boolean masculino = dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY;
        return (masculino ? "Todo " : "Toda ") + nomeDia(dia);
    }

    private static String hora(RecorrenciaDto r) {
        return r.horaEfetiva() == null ? "--:--" : r.horaEfetiva().format(HORA);
    }

    /** "20/11 14:55". */
    public static String diaMesHora(OffsetDateTime quando, ZoneId fuso) {
        return quando == null ? "—" : quando.atZoneSameInstant(fuso).format(DIA_MES_HORA);
    }

    /** "agora", "em 12 min", "em 2 h", "amanhã", "em 3 dias", "atrasado". */
    public static String relativo(OffsetDateTime alvo, ZonedDateTime agora) {
        if (alvo == null) return "";
        ZonedDateTime destino = alvo.atZoneSameInstant(agora.getZone());
        long minutos = Duration.between(agora, destino).toMinutes();
        if (destino.isBefore(agora)) return "atrasado";
        if (minutos < 1) return "agora";
        if (minutos < 60) return "em " + minutos + " min";
        long dias = ChronoUnit.DAYS.between(agora.toLocalDate(), destino.toLocalDate());
        if (dias == 0) return "em " + (minutos / 60) + " h";
        if (dias == 1) return "amanhã";
        return "em " + dias + " dias";
    }
}
