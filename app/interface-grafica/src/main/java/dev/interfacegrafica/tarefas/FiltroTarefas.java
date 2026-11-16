package dev.interfacegrafica.tarefas;

import dev.interfacegrafica.client.dto.TarefasDtos.Prioridade;
import dev.interfacegrafica.client.dto.TarefasDtos.TarefaDto;
import dev.interfacegrafica.lembretes.TextoBusca;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Filtro local do board (busca + prioridade + tags + projeto). Sem JavaFX.
 *
 * @param projetoId    null = todos os projetos do squad; {@link #SEM_PROJETO} = so as sem projeto
 * @param prioridades  vazio = todas
 * @param tags         vazio = todas; senao, a tarefa precisa ter ao menos uma
 */
public record FiltroTarefas(String busca, Set<Prioridade> prioridades, Set<String> tags, Long projetoId)
        implements Predicate<TarefaDto> {

    public static final long SEM_PROJETO = 0L;

    public FiltroTarefas {
        prioridades = prioridades == null ? Set.of() : Set.copyOf(prioridades);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    public static FiltroTarefas nenhum() {
        return new FiltroTarefas("", Set.of(), Set.of(), null);
    }

    public boolean ativo() {
        return (busca != null && !busca.isBlank()) || !prioridades.isEmpty() || !tags.isEmpty();
    }

    @Override
    public boolean test(TarefaDto t) {
        if (projetoId != null) {
            if (projetoId == SEM_PROJETO ? t.projetoId() != null : !projetoId.equals(t.projetoId())) return false;
        }
        if (!prioridades.isEmpty() && !prioridades.contains(t.prioridade())) return false;
        if (!tags.isEmpty() && t.tagsOuVazio().stream().noneMatch(tags::contains)) return false;
        return correspondeBusca(t);
    }

    private boolean correspondeBusca(TarefaDto t) {
        String b = TextoBusca.normalizar(busca).trim();
        if (b.isEmpty()) return true;
        String texto = TextoBusca.normalizar(String.join(" ", t.titulo(), Objects.toString(t.descricao(), ""),
                String.join(" ", t.tagsOuVazio()), Objects.toString(t.motivoBloqueio(), ""),
                Objects.toString(t.projetoSlug(), ""), Objects.toString(t.projetoNome(), "")));
        for (String termo : b.split("\\s+")) {
            if (!texto.contains(termo)) return false;
        }
        return true;
    }

    /** Todas as tags usadas, em ordem alfabetica (para filtro e autocomplete). */
    public static List<String> tagsUsadas(List<TarefaDto> tarefas) {
        return tarefas.stream().flatMap(t -> t.tagsOuVazio().stream()).distinct().sorted().toList();
    }
}
