package dev.interfacegrafica.lembretes;

import dev.interfacegrafica.client.dto.LembreteRequest;
import dev.interfacegrafica.client.dto.ParticipanteDto;
import dev.interfacegrafica.client.dto.RecorrenciaDto;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validacao do formulario e conversao para o corpo da API. Sem JavaFX. */
public final class LembreteFormValidator {

    public enum Campo { TITULO, DATA, HORA, DIA_SEMANA, DIA_MES, ANTECEDENCIA }

    private static final Pattern HORA = Pattern.compile("^([01]?\\d|2[0-3]):([0-5]\\d)$");

    private LembreteFormValidator() { }

    /** Erros por campo; vazio = pode salvar. {@code agora} no fuso da API. */
    public static Map<Campo, String> validar(LembreteForm f, LocalDateTime agora) {
        Map<Campo, String> erros = new EnumMap<>(Campo.class);
        if (vazio(f.titulo())) erros.put(Campo.TITULO, "Informe o título");

        LocalTime hora = hora(f.hora());
        if (hora == null) erros.put(Campo.HORA, vazio(f.hora()) ? "Informe a hora" : "Hora inválida (use HH:mm)");

        switch (f.tipo()) {
            case UNICA -> {
                if (f.data() == null) erros.put(Campo.DATA, "Informe a data");
                else if (hora != null && !f.data().atTime(hora).isAfter(agora))
                    erros.put(Campo.DATA, "A data e hora precisam estar no futuro");
            }
            case SEMANAL -> {
                if (f.diaSemana() == null) erros.put(Campo.DIA_SEMANA, "Escolha o dia da semana");
            }
            case MENSAL -> {
                if (f.diaMes() == null || f.diaMes() < 1 || f.diaMes() > 31)
                    erros.put(Campo.DIA_MES, "Dia do mês entre 1 e 31");
            }
            case DIARIA -> { }
        }
        if (f.antecedenciaMin() == null || f.antecedenciaMin() < 0)
            erros.put(Campo.ANTECEDENCIA, "Antecedência deve ser 0 ou mais");
        return erros;
    }

    /** Monta o corpo do POST/PUT. So chamar com o formulario valido. */
    public static LembreteRequest paraRequest(LembreteForm f) {
        LocalTime hora = hora(f.hora());
        RecorrenciaDto rec = switch (f.tipo()) {
            case UNICA -> RecorrenciaDto.unica(f.data().atTime(hora));
            case DIARIA -> RecorrenciaDto.diaria(hora);
            case SEMANAL -> RecorrenciaDto.semanal(f.diaSemana(), hora);
            case MENSAL -> RecorrenciaDto.mensal(f.diaMes(), hora);
        };
        List<ParticipanteDto> participantes = f.participantes() == null ? List.of() : f.participantes().stream()
                .filter(p -> !vazio(p.nome()))
                .map(p -> new ParticipanteDto(p.nome().trim(), limpar(p.detalhe())))
                .toList();
        return new LembreteRequest(f.titulo().trim(), limpar(f.descricao()), limpar(f.mensagem()), rec,
                f.antecedenciaMin(), limpar(f.contextoMd()), participantes);
    }

    /** "9:00" / "09:00" -> LocalTime; invalido -> null. */
    public static LocalTime hora(String texto) {
        if (texto == null) return null;
        Matcher m = HORA.matcher(texto.trim());
        return m.matches() ? LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))) : null;
    }

    private static boolean vazio(String s) { return s == null || s.isBlank(); }

    private static String limpar(String s) { return vazio(s) ? null : s.strip(); }
}
