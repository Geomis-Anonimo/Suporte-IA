package dev.interfacegrafica.tarefas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.interfacegrafica.client.dto.TarefasDtos.Prioridade;
import dev.interfacegrafica.client.dto.TarefasDtos.Status;
import dev.interfacegrafica.client.dto.TarefasDtos.TarefaDto;
import dev.interfacegrafica.client.dto.TarefasDtos.TarefaRequest;
import dev.interfacegrafica.tarefas.TarefaFormValidator.Campo;
import dev.interfacegrafica.tarefas.TarefaFormValidator.Form;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Classes puras da tela de tarefas: Slug, FiltroTarefas e TarefaFormValidator. */
class TarefasPurasTest {

    private static TarefaDto tarefa(long id, Long projetoId, String titulo, Prioridade p, List<String> tags, String motivo) {
        return new TarefaDto(id, 1, "guardian", projetoId, projetoId == null ? null : "guardian_whatsapp",
                projetoId == null ? null : "guardian_whatsapp", titulo, null,
                motivo == null ? Status.A_FAZER : Status.BLOQUEADO, motivo, p, tags, null, 1, null, null, null);
    }

    @Test
    void slug() {
        assertEquals("tela-web", Slug.gerar("Tela Web"));
        assertEquals("acao-nova", Slug.gerar("  Ação   Nova! "));
        assertEquals("guardian_whatsapp", Slug.gerar("guardian_whatsapp"));
        assertTrue(Slug.valido("bca-ura-ws"));
        assertFalse(Slug.valido("Tela Web"));
        assertFalse(Slug.valido("-x"));
        assertFalse(Slug.valido(""));
    }

    @Test
    void filtroCombinado() {
        var a = tarefa(1, 4L, "Validar template Omne", Prioridade.ALTA, List.of("gmud", "omne"), null);
        var b = tarefa(2, null, "Pedir acesso à VPN", Prioridade.MEDIA, List.of(), null);
        var c = tarefa(3, 4L, "Subir PR", Prioridade.BAIXA, List.of("pr"), "aguardando revisão do Marcelo");
        List<TarefaDto> todas = List.of(a, b, c);

        assertEquals(3, todas.stream().filter(FiltroTarefas.nenhum()).count());
        assertEquals(List.of(b), filtrar(todas, new FiltroTarefas("acesso a vpn", null, null, null)));
        assertEquals(List.of(c), filtrar(todas, new FiltroTarefas("MARCELO", null, null, null)), "busca no motivo");
        assertEquals(List.of(a, c), filtrar(todas, new FiltroTarefas("whatsapp", null, null, null)), "busca no projeto");
        assertEquals(List.of(a, c), filtrar(todas, new FiltroTarefas("", Set.of(Prioridade.ALTA, Prioridade.BAIXA), null, null)));
        assertEquals(List.of(a, c), filtrar(todas, new FiltroTarefas("", null, Set.of("pr", "gmud"), null)), "qualquer das tags");
        assertEquals(List.of(b), filtrar(todas, new FiltroTarefas("", null, null, FiltroTarefas.SEM_PROJETO)));
        assertEquals(List.of(a, c), filtrar(todas, new FiltroTarefas("", null, null, 4L)));
        assertEquals(List.of(c), filtrar(todas, new FiltroTarefas("subir", Set.of(Prioridade.BAIXA), Set.of("pr"), 4L)));
        assertFalse(FiltroTarefas.nenhum().ativo());
        assertTrue(new FiltroTarefas("x", null, null, null).ativo());
        assertEquals(List.of("gmud", "omne", "pr"), FiltroTarefas.tagsUsadas(todas));
    }

    private static List<TarefaDto> filtrar(List<TarefaDto> l, FiltroTarefas f) {
        return l.stream().filter(f).toList();
    }

    @Test
    void validacaoDoFormulario() {
        Form vazio = new Form("", null, null, Status.A_FAZER, "", Prioridade.MEDIA, List.of(), "", "");
        Map<Campo, String> erros = TarefaFormValidator.validar(vazio);
        assertTrue(erros.containsKey(Campo.TITULO));
        assertTrue(erros.containsKey(Campo.SQUAD));

        Form bloqueadoSemMotivo = new Form("X", 1L, null, Status.BLOQUEADO, " ", null, List.of(), null, null);
        assertTrue(TarefaFormValidator.validar(bloqueadoSemMotivo).containsKey(Campo.MOTIVO));

        Form linkRuim = new Form("X", 1L, null, Status.A_FAZER, null, null, List.of("tag com espaco"), "ftp://x", null);
        Map<Campo, String> e2 = TarefaFormValidator.validar(linkRuim);
        assertTrue(e2.containsKey(Campo.LINK));
        assertTrue(e2.containsKey(Campo.TAGS));

        assertTrue(TarefaFormValidator.linkValido("guardian/dailies/2026-11-16-daily.md"));
        assertTrue(TarefaFormValidator.linkValido("https://dev.azure.com/org/_workitems/edit/1"));
        assertFalse(TarefaFormValidator.linkValido("../fora.md"));
        assertTrue(TarefaFormValidator.validar(new Form("x".repeat(201), 1L, null, null, null, null, null, null, null))
                .containsKey(Campo.TITULO));
    }

    @Test
    void paraRequest() {
        Form f = new Form("  Validar  ", 1L, 4L, Status.FAZENDO, "motivo ignorado", Prioridade.ALTA,
                List.of("GMUD", " daily ", "gmud"), "  ", " detalhes ");
        TarefaRequest r = TarefaFormValidator.paraRequest(f);
        assertEquals("Validar", r.titulo());
        assertNull(r.motivoBloqueio(), "motivo so em BLOQUEADO");
        assertEquals(List.of("gmud", "daily"), r.tags());
        assertNull(r.link());
        assertEquals("detalhes", r.descricao());
        assertEquals(4L, r.projetoId());
    }
}
