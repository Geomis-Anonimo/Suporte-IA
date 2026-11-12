package dev.lembretes.lembrete.presentation.dto;

import dev.lembretes.lembrete.core.domain.Recorrencia;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Recorrencia na API. Campos exigidos por tipo:
 * UNICA -> dataHora; DIARIA -> hora; SEMANAL -> diaSemana + hora; MENSAL -> diaMes + hora.
 */
public record RecorrenciaDto(
        @NotNull Tipo tipo,
        LocalDateTime dataHora,
        LocalTime hora,
        DayOfWeek diaSemana,
        Integer diaMes) {

    public enum Tipo { UNICA, DIARIA, SEMANAL, MENSAL }

    public Recorrencia paraDominio() {
        return switch (tipo) {
            case UNICA -> new Recorrencia.Unica(exigir(dataHora, "dataHora"));
            case DIARIA -> new Recorrencia.Diaria(exigir(hora, "hora"));
            case SEMANAL -> new Recorrencia.Semanal(exigir(diaSemana, "diaSemana"), exigir(hora, "hora"));
            case MENSAL -> new Recorrencia.Mensal(exigir(diaMes, "diaMes"), exigir(hora, "hora"));
        };
    }

    public static RecorrenciaDto de(Recorrencia r) {
        if (r instanceof Recorrencia.Unica u) return new RecorrenciaDto(Tipo.UNICA, u.dataHora(), null, null, null);
        if (r instanceof Recorrencia.Diaria d) return new RecorrenciaDto(Tipo.DIARIA, null, d.hora(), null, null);
        if (r instanceof Recorrencia.Semanal s) return new RecorrenciaDto(Tipo.SEMANAL, null, s.hora(), s.diaSemana(), null);
        Recorrencia.Mensal m = (Recorrencia.Mensal) r;
        return new RecorrenciaDto(Tipo.MENSAL, null, m.hora(), null, m.diaMes());
    }

    private static <T> T exigir(T valor, String campo) {
        if (valor == null) throw new IllegalArgumentException("recorrencia." + campo + " e obrigatorio para esse tipo");
        return valor;
    }
}
