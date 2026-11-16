package dev.api.lembretes.core.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

/** Quando um lembrete acontece. Calcula a proxima ocorrencia a partir de um instante. */
public sealed interface Recorrencia {

    LocalTime hora();

    boolean ocorreEm(LocalDate dia);

    /** Ocorrencia unica, em data/hora especifica. */
    record Unica(LocalDateTime dataHora) implements Recorrencia {
        public LocalTime hora() { return dataHora.toLocalTime(); }
        public boolean ocorreEm(LocalDate dia) { return dia.equals(dataHora.toLocalDate()); }
    }

    /** Todo dia, no mesmo horario. */
    record Diaria(LocalTime hora) implements Recorrencia {
        public boolean ocorreEm(LocalDate dia) { return true; }
    }

    /** Toda semana, em um dia da semana. */
    record Semanal(DayOfWeek diaSemana, LocalTime hora) implements Recorrencia {
        public boolean ocorreEm(LocalDate dia) { return dia.getDayOfWeek() == diaSemana; }
    }

    /** Todo mes, em um dia do mes (meses sem esse dia sao pulados). */
    record Mensal(int diaMes, LocalTime hora) implements Recorrencia {
        public Mensal {
            if (diaMes < 1 || diaMes > 31) throw new IllegalArgumentException("diaMes deve estar entre 1 e 31");
        }
        public boolean ocorreEm(LocalDate dia) { return dia.getDayOfMonth() == diaMes; }
    }

    /** Primeira ocorrencia em ou depois de {@code ref}. Vazio se nao houver mais (ex.: unica no passado). */
    default Optional<Instant> aPartirDe(Instant ref, ZoneId fuso) {
        return buscar(ref, fuso, true);
    }

    /** Primeira ocorrencia estritamente depois de {@code ref}. */
    default Optional<Instant> apos(Instant ref, ZoneId fuso) {
        return buscar(ref, fuso, false);
    }

    private Optional<Instant> buscar(Instant ref, ZoneId fuso, boolean inclusivo) {
        LocalDate inicio = ref.atZone(fuso).toLocalDate();
        for (int i = 0; i < 800; i++) {
            LocalDate dia = inicio.plusDays(i);
            if (!ocorreEm(dia)) continue;
            Instant candidata = dia.atTime(hora()).atZone(fuso).toInstant();
            if (inclusivo ? !candidata.isBefore(ref) : candidata.isAfter(ref)) return Optional.of(candidata);
        }
        return Optional.empty();
    }
}
