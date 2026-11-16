package dev.interfacegrafica.lembretes;

import dev.interfacegrafica.client.dto.LembreteResponse;
import dev.interfacegrafica.client.dto.ParticipanteDto;
import dev.interfacegrafica.client.dto.RecorrenciaDto;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Valores "crus" do formulario, como o usuario digitou (hora em texto, dia do mes pode ser invalido).
 * Record: comparar dois snapshots diz se houve alteracao nao salva.
 */
public record LembreteForm(
        String titulo,
        String descricao,
        String mensagem,
        RecorrenciaDto.Tipo tipo,
        LocalDate data,
        String hora,
        DayOfWeek diaSemana,
        Integer diaMes,
        Integer antecedenciaMin,
        String contextoMd,
        List<ParticipanteDto> participantes) {

    public static final int ANTECEDENCIA_PADRAO = 5;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public static LembreteForm vazio() {
        return new LembreteForm("", "", "", RecorrenciaDto.Tipo.UNICA, null, "", null, null,
                ANTECEDENCIA_PADRAO, "", List.of());
    }

    public static LembreteForm de(LembreteResponse l) {
        RecorrenciaDto r = l.recorrencia();
        return new LembreteForm(
                nz(l.titulo()), nz(l.descricao()), nz(l.mensagem()),
                r.tipo(),
                r.dataHora() == null ? null : r.dataHora().toLocalDate(),
                r.horaEfetiva() == null ? "" : r.horaEfetiva().format(HORA),
                r.diaSemana(), r.diaMes(),
                l.antecedenciaMin(), nz(l.contextoMd()),
                l.participantesOuVazio().stream().map(p -> new ParticipanteDto(nz(p.nome()), nz(p.detalhe()))).toList());
    }

    public LembreteForm comTitulo(String novo) {
        return new LembreteForm(novo, descricao, mensagem, tipo, data, hora, diaSemana, diaMes, antecedenciaMin,
                contextoMd, participantes);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
