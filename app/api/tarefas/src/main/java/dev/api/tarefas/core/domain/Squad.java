package dev.api.tarefas.core.domain;

/** Squad (time). Equivale a uma "Feature" do Azure Boards: agrupa projetos e tarefas. */
public record Squad(Long id, String slug, String nome, int ordem, boolean arquivado) {

    public Squad {
        slug = Slug.validar(slug, "slug");
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("nome e obrigatorio");
        nome = nome.strip();
    }

    public Squad comId(long novoId) {
        return new Squad(novoId, slug, nome, ordem, arquivado);
    }
}
