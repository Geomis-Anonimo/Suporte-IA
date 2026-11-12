package dev.lembretes.lembrete.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lembretes.lembrete.core.application.DispararLembretesVencidosService;
import dev.lembretes.lembrete.core.application.MontadorAviso;
import dev.lembretes.lembrete.core.application.port.DisparoRepository;
import dev.lembretes.lembrete.core.application.port.LembreteRepository;
import dev.lembretes.lembrete.core.application.port.NotificadorPort;
import dev.lembretes.lembrete.core.application.port.RelogioPort;
import dev.lembretes.lembrete.core.domain.Lembrete;
import dev.lembretes.lembrete.core.domain.Participante;
import dev.lembretes.lembrete.core.domain.Recorrencia;
import dev.lembretes.lembrete.core.domain.StatusDisparo;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DispararLembretesVencidosServiceTest {
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private Instant agora;
    private final List<Lembrete> banco = new ArrayList<>();
    private final List<String> enviadas = new ArrayList<>();
    private final List<StatusDisparo> status = new ArrayList<>();
    private boolean discordFora;
    private DispararLembretesVencidosService servico;

    @BeforeEach
    void montar() {
        agora = LocalDateTime.parse("2026-11-13T14:54:00").atZone(SP).toInstant(); // 6 min antes das 15h
        RelogioPort relogio = new RelogioPort() {
            public Instant agora() { return agora; }
            public ZoneId fuso() { return SP; }
        };
        LembreteRepository repo = new LembreteRepository() {
            public Lembrete salvar(Lembrete l) { banco.removeIf(x -> x.id().equals(l.id())); banco.add(l); return l; }
            public Optional<Lembrete> buscarPorId(long id) { return banco.stream().filter(x -> x.id() == id).findFirst(); }
            public List<Lembrete> listar(Boolean ativo) { return List.copyOf(banco); }
            public List<Lembrete> buscarComDisparoAte(Instant limite) {
                return banco.stream().filter(l -> l.ativo() && l.proximoDisparo() != null
                        && !l.proximoDisparo().isAfter(limite)).toList();
            }
            public boolean remover(long id) { return banco.removeIf(x -> x.id() == id); }
        };
        DisparoRepository disparos = (id, oc, em, st, det) -> status.add(st);
        NotificadorPort notificador = msg -> {
            if (discordFora) throw new NotificadorPort.NotificacaoException("fora");
            enviadas.add(msg);
        };
        var montador = new MontadorAviso();
        servico = new DispararLembretesVencidosService(repo, disparos, notificador, montador, relogio,
                Duration.ofMinutes(1), Duration.ofMinutes(10));
    }

    private Lembrete reuniao() {
        Instant ocorrencia = LocalDateTime.parse("2026-11-13T15:00:00").atZone(SP).toInstant();
        return new Lembrete(1L, "Alinhamento Mercado Pago", null,
                "Vamos fechar a subida em producao; ficou pendente validar o webhook.",
                new Recorrencia.Unica(LocalDateTime.parse("2026-11-13T15:00:00")), 5, "guardian/x.md",
                List.of(new Participante("Mario", "empresa X")), ocorrencia, true, agora);
    }

    @Test
    void avisaQuandoFaltam6MinEEncerraUnica() {
        banco.add(reuniao());
        assertEquals(1, servico.executar());
        assertTrue(enviadas.get(0).contains("Daqui 6 min"));
        assertTrue(enviadas.get(0).contains("ficou pendente validar o webhook"));
        assertTrue(enviadas.get(0).contains("Mario (empresa X)"));
        assertTrue(enviadas.get(0).contains("guardian/x.md"));
        assertFalse(banco.get(0).ativo());
        assertEquals(0, servico.executar()); // nao duplica
    }

    @Test
    void naoAvisaAntesDaJanela() {
        banco.add(reuniao());
        agora = agora.minus(Duration.ofMinutes(3)); // faltam 9 min
        assertEquals(0, servico.executar());
    }

    @Test
    void discordForaMantemPendenteParaNovaTentativa() {
        banco.add(reuniao());
        discordFora = true;
        assertEquals(0, servico.executar());
        assertTrue(banco.get(0).ativo());
        discordFora = false;
        assertEquals(1, servico.executar());
    }

    @Test
    void descartaQuandoPassouDaTolerancia() {
        banco.add(reuniao());
        agora = agora.plus(Duration.ofMinutes(30));
        assertEquals(0, servico.executar());
        assertEquals(List.of(StatusDisparo.EXPIRADO), status);
        assertFalse(banco.get(0).ativo());
    }
}
