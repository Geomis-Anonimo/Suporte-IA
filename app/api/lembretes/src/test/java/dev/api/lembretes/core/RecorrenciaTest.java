package dev.api.lembretes.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.api.lembretes.core.domain.Recorrencia;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RecorrenciaTest {
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private static Instant em(String isoLocal) {
        return LocalDateTime.parse(isoLocal).atZone(SP).toInstant();
    }

    @Test
    void semanalProximaQuinta() {
        var r = new Recorrencia.Semanal(DayOfWeek.THURSDAY, LocalTime.of(9, 0));
        // 2026-11-12 e quinta; as 10h ja passou das 9h -> proxima quinta
        assertEquals(em("2026-11-19T09:00"), r.aPartirDe(em("2026-11-12T10:00"), SP).orElseThrow());
        assertEquals(em("2026-11-12T09:00"), r.aPartirDe(em("2026-11-12T08:00"), SP).orElseThrow());
    }

    @Test
    void aposEEstritamenteDepois() {
        var r = new Recorrencia.Diaria(LocalTime.of(9, 0));
        assertEquals(em("2026-11-13T09:00"), r.apos(em("2026-11-12T09:00"), SP).orElseThrow());
    }

    @Test
    void unicaNoPassadoNaoTemOcorrencia() {
        var r = new Recorrencia.Unica(LocalDateTime.parse("2026-11-12T15:00"));
        assertTrue(r.aPartirDe(em("2026-11-13T00:00"), SP).isEmpty());
        assertEquals(em("2026-11-12T15:00"), r.aPartirDe(em("2026-11-12T14:00"), SP).orElseThrow());
    }

    @Test
    void mensalDia31PulaMesesCurtos() {
        var r = new Recorrencia.Mensal(31, LocalTime.of(8, 0));
        assertEquals(em("2026-12-31T08:00"), r.apos(em("2026-11-12T00:00"), SP).orElseThrow());
    }
}
