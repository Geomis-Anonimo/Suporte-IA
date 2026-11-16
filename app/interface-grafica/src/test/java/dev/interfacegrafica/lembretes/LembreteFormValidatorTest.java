package dev.interfacegrafica.lembretes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.interfacegrafica.client.dto.LembreteRequest;
import dev.interfacegrafica.client.dto.LembreteResponse;
import dev.interfacegrafica.client.dto.ParticipanteDto;
import dev.interfacegrafica.client.dto.RecorrenciaDto;
import dev.interfacegrafica.client.dto.RecorrenciaDto.Tipo;
import dev.interfacegrafica.lembretes.LembreteFormValidator.Campo;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LembreteFormValidatorTest {
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 16, 10, 0);

    private static LembreteForm form(Tipo tipo, LocalDate data, String hora, DayOfWeek dia, Integer diaMes) {
        return new LembreteForm("Reunião", "", "", tipo, data, hora, dia, diaMes, 5, "", List.of());
    }

    @Test
    void formularioVazioPedeTituloEHora() {
        Map<Campo, String> erros = LembreteFormValidator.validar(LembreteForm.vazio(), AGORA);
        assertTrue(erros.containsKey(Campo.TITULO));
        assertTrue(erros.containsKey(Campo.HORA));
        assertTrue(erros.containsKey(Campo.DATA));
    }

    @Test
    void unicaPrecisaEstarNoFuturo() {
        var passado = form(Tipo.UNICA, LocalDate.of(2026, 11, 16), "09:59", null, null);
        assertEquals("A data e hora precisam estar no futuro", LembreteFormValidator.validar(passado, AGORA).get(Campo.DATA));
        var futuro = form(Tipo.UNICA, LocalDate.of(2026, 11, 16), "10:01", null, null);
        assertTrue(LembreteFormValidator.validar(futuro, AGORA).isEmpty());
    }

    @Test
    void camposObrigatoriosPorTipo() {
        assertTrue(LembreteFormValidator.validar(form(Tipo.SEMANAL, null, "09:00", null, null), AGORA).containsKey(Campo.DIA_SEMANA));
        assertTrue(LembreteFormValidator.validar(form(Tipo.MENSAL, null, "09:00", null, null), AGORA).containsKey(Campo.DIA_MES));
        assertTrue(LembreteFormValidator.validar(form(Tipo.MENSAL, null, "09:00", null, 32), AGORA).containsKey(Campo.DIA_MES));
        assertTrue(LembreteFormValidator.validar(form(Tipo.DIARIA, null, "09:00", null, null), AGORA).isEmpty());
    }

    @Test
    void horaInvalida() {
        assertEquals("Hora inválida (use HH:mm)",
                LembreteFormValidator.validar(form(Tipo.DIARIA, null, "25:00", null, null), AGORA).get(Campo.HORA));
        assertEquals(LocalTime.of(9, 5), LembreteFormValidator.hora("9:05"));
        assertNull(LembreteFormValidator.hora("9:5"));
    }

    @Test
    void antecedenciaObrigatoria() {
        var f = new LembreteForm("X", "", "", Tipo.DIARIA, null, "09:00", null, null, null, "", List.of());
        assertTrue(LembreteFormValidator.validar(f, AGORA).containsKey(Campo.ANTECEDENCIA));
    }

    @Test
    void paraRequestLimpaEFiltra() {
        var f = new LembreteForm("  Daily  ", "  ", "Texto\n", Tipo.SEMANAL, LocalDate.of(2026, 1, 1), "9:00",
                DayOfWeek.MONDAY, 10, 0, "",
                List.of(new ParticipanteDto("Ana", " PO "), new ParticipanteDto("  ", "sem nome")));

        LembreteRequest req = LembreteFormValidator.paraRequest(f);

        assertEquals("Daily", req.titulo());
        assertNull(req.descricao());
        assertEquals("Texto", req.mensagem());
        assertNull(req.contextoMd());
        assertEquals(0, req.antecedenciaMin());
        assertEquals(RecorrenciaDto.semanal(DayOfWeek.MONDAY, LocalTime.of(9, 0)), req.recorrencia());
        assertEquals(List.of(new ParticipanteDto("Ana", "PO")), req.participantes());
    }

    @Test
    void formDeResponseVoltaIgual() {
        var resp = new LembreteResponse(1L, "T", null, "M", RecorrenciaDto.mensal(31, LocalTime.of(8, 0)), 10, null,
                List.of(new ParticipanteDto("Ana", null)), null, null, true);
        LembreteForm f = LembreteForm.de(resp);
        assertEquals("08:00", f.hora());
        assertEquals(31, f.diaMes());
        assertEquals("", f.descricao());
        LembreteRequest req = LembreteFormValidator.paraRequest(f);
        assertEquals(resp.recorrencia(), req.recorrencia());
        assertEquals(10, req.antecedenciaMin());
    }

    @Test
    void buscaIgnoraAcentoEMaiuscula() {
        var resp = new LembreteResponse(1L, "Reunião de Alinhamento", null, null, RecorrenciaDto.diaria(LocalTime.NOON), 5,
                "guardian/README.md", List.of(new ParticipanteDto("Márcio", "gerente")), null, null, true);
        assertTrue(TextoBusca.corresponde(resp, "reuniao"));
        assertTrue(TextoBusca.corresponde(resp, "MARCIO gerente"));
        assertTrue(TextoBusca.corresponde(resp, "guardian"));
        assertTrue(!TextoBusca.corresponde(resp, "pld"));
    }
}
