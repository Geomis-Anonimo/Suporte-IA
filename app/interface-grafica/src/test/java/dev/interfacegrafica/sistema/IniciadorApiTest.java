package dev.interfacegrafica.sistema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IniciadorApiTest {

    @Test
    void semPastaClaudeFalhaComMensagemClara() {
        IniciadorApi iniciador = new IniciadorApi(null, () -> true);
        IniciadorApi.FalhaAoIniciar e = assertThrows(IniciadorApi.FalhaAoIniciar.class, () -> iniciador.iniciar(etapa -> { }));
        assertTrue(e.getMessage().contains("CLAUDE_DIR"));
    }

    @Test
    void ultimasLinhasIgnoraVaziasEJunta() {
        assertEquals("b | c", IniciadorApi.ultimasLinhas("a\n\nb\r\n  c  \n\n", 2));
        assertEquals("(sem saída)", IniciadorApi.ultimasLinhas("  \n", 3));
        assertEquals("(sem saída)", IniciadorApi.ultimasLinhas(null, 3));
    }

    @Test
    void esperarRepeteAteDarCertoOuEstourar() throws InterruptedException {
        AtomicInteger tentativas = new AtomicInteger();
        assertTrue(IniciadorApi.esperar(() -> tentativas.incrementAndGet() >= 3, Duration.ofSeconds(5), Duration.ofMillis(1)));
        assertEquals(3, tentativas.get());
        assertFalse(IniciadorApi.esperar(() -> false, Duration.ofMillis(30), Duration.ofMillis(5)));
    }

    @Test
    void comandoInexistenteNaoLancaExcecao() {
        IniciadorApi.Resultado r = IniciadorApi.rodar(null, Duration.ofSeconds(5), "comando-que-nao-existe-xyz");
        assertEquals(-1, r.codigo());
    }
}
