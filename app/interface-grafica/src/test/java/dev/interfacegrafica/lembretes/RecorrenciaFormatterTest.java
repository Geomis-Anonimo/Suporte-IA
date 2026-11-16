package dev.interfacegrafica.lembretes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.interfacegrafica.client.dto.RecorrenciaDto;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class RecorrenciaFormatterTest {
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Test
    void quatroTipos() {
        assertEquals("Único · 20/11 15:00",
                RecorrenciaFormatter.formatar(RecorrenciaDto.unica(LocalDateTime.of(2026, 11, 20, 15, 0)), 2026));
        assertEquals("Único · 05/01/2027 15:00",
                RecorrenciaFormatter.formatar(RecorrenciaDto.unica(LocalDateTime.of(2027, 1, 5, 15, 0)), 2026));
        assertEquals("Todo dia · 09:00", RecorrenciaFormatter.formatar(RecorrenciaDto.diaria(LocalTime.of(9, 0))));
        assertEquals("Toda quinta · 09:00",
                RecorrenciaFormatter.formatar(RecorrenciaDto.semanal(DayOfWeek.THURSDAY, LocalTime.of(9, 0))));
        assertEquals("Todo dia 15 · 18:30", RecorrenciaFormatter.formatar(RecorrenciaDto.mensal(15, LocalTime.of(18, 30))));
    }

    @Test
    void generoDoDiaDaSemana() {
        assertEquals("Todo sábado · 10:00",
                RecorrenciaFormatter.formatar(RecorrenciaDto.semanal(DayOfWeek.SATURDAY, LocalTime.of(10, 0))));
        assertEquals("Todo domingo · 10:00",
                RecorrenciaFormatter.formatar(RecorrenciaDto.semanal(DayOfWeek.SUNDAY, LocalTime.of(10, 0))));
        assertEquals("Toda terça · 10:00",
                RecorrenciaFormatter.formatar(RecorrenciaDto.semanal(DayOfWeek.TUESDAY, LocalTime.of(10, 0))));
    }

    @Test
    void nomesCompletosDosDias() {
        assertEquals("Segunda-feira", RecorrenciaFormatter.nomeDiaCompleto(DayOfWeek.MONDAY));
        assertEquals("Terça-feira", RecorrenciaFormatter.nomeDiaCompleto(DayOfWeek.TUESDAY));
        assertEquals("Sábado", RecorrenciaFormatter.nomeDiaCompleto(DayOfWeek.SATURDAY));
        assertEquals("Domingo", RecorrenciaFormatter.nomeDiaCompleto(DayOfWeek.SUNDAY));
    }

    @Test
    void relativo() {
        ZonedDateTime agora = ZonedDateTime.of(2026, 11, 16, 10, 0, 0, 0, SP);
        OffsetDateTime base = agora.toOffsetDateTime();
        assertEquals("agora", RecorrenciaFormatter.relativo(base.plusSeconds(30), agora));
        assertEquals("em 12 min", RecorrenciaFormatter.relativo(base.plusMinutes(12), agora));
        assertEquals("em 2 h", RecorrenciaFormatter.relativo(base.plusHours(2), agora));
        assertEquals("amanhã", RecorrenciaFormatter.relativo(base.plusDays(1), agora));
        assertEquals("em 3 dias", RecorrenciaFormatter.relativo(base.plusDays(3), agora));
        assertEquals("atrasado", RecorrenciaFormatter.relativo(base.minusMinutes(5), agora));
    }

    @Test
    void diaMesHoraConverteParaOFuso() {
        OffsetDateTime utc = OffsetDateTime.of(2026, 11, 20, 17, 55, 0, 0, ZoneOffset.UTC);
        assertEquals("20/11 14:55", RecorrenciaFormatter.diaMesHora(utc, SP));
        assertEquals("—", RecorrenciaFormatter.diaMesHora(null, SP));
    }
}
