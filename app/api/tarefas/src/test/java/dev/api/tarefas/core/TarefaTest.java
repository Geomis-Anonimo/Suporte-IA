package dev.api.tarefas.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.api.tarefas.core.domain.Prioridade;
import dev.api.tarefas.core.domain.Projeto;
import dev.api.tarefas.core.domain.Slug;
import dev.api.tarefas.core.domain.StatusTarefa;
import dev.api.tarefas.core.domain.Tarefa;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TarefaTest {
    private static final Instant T0 = Instant.parse("2026-11-16T12:00:00Z");

    private static Tarefa tarefa(StatusTarefa status, String motivo, List<String> tags, String link) {
        return new Tarefa(1L, 1, null, "Titulo", null, status, motivo, null, tags, link, 1024, T0, T0, T0);
    }

    @Test
    void padroesEMotivoSoEmBloqueado() {
        Tarefa t = new Tarefa(null, 1, null, "  Fazer X  ", "  ", null, "motivo ignorado", null, null, null, 1, T0, T0, T0);
        assertThat(t.titulo()).isEqualTo("Fazer X");
        assertThat(t.descricao()).isNull();
        assertThat(t.status()).isEqualTo(StatusTarefa.A_FAZER);
        assertThat(t.prioridade()).isEqualTo(Prioridade.MEDIA);
        assertThat(t.motivoBloqueio()).isNull();
        assertThat(t.concluidaEm()).as("so FEITO tem concluidaEm").isNull();
    }

    @Test
    void bloqueadoExigeMotivo() {
        assertThatThrownBy(() -> tarefa(StatusTarefa.BLOQUEADO, " ", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motivoBloqueio");
        assertThat(tarefa(StatusTarefa.BLOQUEADO, "aguardando DevOps", null, null).motivoBloqueio()).isEqualTo("aguardando DevOps");
    }

    @Test
    void comStatusControlaConcluidaEmEMotivo() {
        Instant t1 = T0.plusSeconds(60);
        Tarefa aFazer = tarefa(StatusTarefa.A_FAZER, null, null, null);
        Tarefa feita = aFazer.comStatus(StatusTarefa.FEITO, null, 5, t1);
        assertThat(feita.concluidaEm()).isEqualTo(t1);
        assertThat(feita.atualizadaEm()).isEqualTo(t1);
        assertThat(feita.comStatus(StatusTarefa.FEITO, null, 5, t1.plusSeconds(60)).concluidaEm())
                .as("continuar em FEITO mantem a data").isEqualTo(t1);
        assertThat(feita.comStatus(StatusTarefa.FAZENDO, null, 5, t1).concluidaEm()).isNull();
        Tarefa bloqueada = aFazer.comStatus(StatusTarefa.BLOQUEADO, "espera", 5, t1);
        assertThat(bloqueada.comStatus(StatusTarefa.FAZENDO, "espera", 5, t1).motivoBloqueio()).isNull();
    }

    @Test
    void tagsNormalizadas() {
        assertThat(tarefa(StatusTarefa.A_FAZER, null, Arrays.asList(" GMUD ", "bug", "gmud", "", null), null).tags())
                .containsExactly("gmud", "bug");
        assertThatThrownBy(() -> tarefa(StatusTarefa.A_FAZER, null, List.of("duas palavras"), null))
                .hasMessageContaining("espacos");
        assertThatThrownBy(() -> tarefa(StatusTarefa.A_FAZER, null, List.of("x".repeat(31)), null))
                .hasMessageContaining("30");
    }

    @Test
    void linkUrlOuCaminhoDocs() {
        assertThat(tarefa(StatusTarefa.A_FAZER, null, null, "https://dev.azure.com/x/_workitems/1").link()).startsWith("https");
        assertThat(tarefa(StatusTarefa.A_FAZER, null, null, "guardian/dailies/2026-11-16-daily.md").link()).endsWith(".md");
        for (String ruim : List.of("ftp://x", "/etc/passwd.md", "../fora.md", "docs/sem-extensao")) {
            assertThatThrownBy(() -> tarefa(StatusTarefa.A_FAZER, null, null, ruim)).as(ruim).hasMessageContaining("link");
        }
    }

    @Test
    void slugEContextoDoProjeto() {
        assertThat(Slug.validar(" guardian_whatsapp ", "slug")).isEqualTo("guardian_whatsapp");
        assertThat(Slug.validar("tela-web", "slug")).isEqualTo("tela-web");
        for (String ruim : List.of("Tela Web", "-x", "ção", "")) {
            assertThatThrownBy(() -> Slug.validar(ruim, "slug")).as(ruim).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new Projeto(null, 1, "p", "P", null, "/abs.md", false)).hasMessageContaining("contextoMd");
    }

    @Test
    void tituloLongo() {
        assertThatThrownBy(() -> new Tarefa(null, 1, null, "x".repeat(201), null, null, null, null, null, null, 1, T0, T0, null))
                .hasMessageContaining("200");
    }
}
