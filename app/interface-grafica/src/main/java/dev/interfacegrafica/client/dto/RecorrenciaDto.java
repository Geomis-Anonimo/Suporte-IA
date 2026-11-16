package dev.interfacegrafica.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Recorrencia como a API espera/devolve. Campos por tipo:
 * UNICA -> dataHora; DIARIA -> hora; SEMANAL -> diaSemana + hora; MENSAL -> diaMes + hora.
 * Campos nulos nao sao enviados.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecorrenciaDto(Tipo tipo, LocalDateTime dataHora, LocalTime hora, DayOfWeek diaSemana, Integer diaMes) {

    public enum Tipo { UNICA, DIARIA, SEMANAL, MENSAL }

    public static RecorrenciaDto unica(LocalDateTime dataHora) { return new RecorrenciaDto(Tipo.UNICA, dataHora, null, null, null); }

    public static RecorrenciaDto diaria(LocalTime hora) { return new RecorrenciaDto(Tipo.DIARIA, null, hora, null, null); }

    public static RecorrenciaDto semanal(DayOfWeek dia, LocalTime hora) { return new RecorrenciaDto(Tipo.SEMANAL, null, hora, dia, null); }

    public static RecorrenciaDto mensal(int diaMes, LocalTime hora) { return new RecorrenciaDto(Tipo.MENSAL, null, hora, null, diaMes); }

    /** Hora da ocorrencia, qualquer que seja o tipo. */
    public LocalTime horaEfetiva() {
        return tipo == Tipo.UNICA && dataHora != null ? dataHora.toLocalTime() : hora;
    }
}
