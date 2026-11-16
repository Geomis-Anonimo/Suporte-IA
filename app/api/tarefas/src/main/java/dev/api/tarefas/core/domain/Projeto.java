package dev.api.tarefas.core.domain;

/**
 * Projeto dentro de um squad (ex.: guardian_whatsapp).
 *
 * @param contextoMd caminho do README do projeto, relativo a docs/ (so referencia)
 */
public record Projeto(Long id, long squadId, String slug, String nome, String descricao, String contextoMd,
                      boolean arquivado) {

    public Projeto {
        slug = Slug.validar(slug, "slug");
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("nome e obrigatorio");
        nome = nome.strip();
        descricao = Textos.limpar(descricao);
        contextoMd = Textos.limpar(contextoMd);
        if (contextoMd != null && !Textos.caminhoDocsValido(contextoMd))
            throw new IllegalArgumentException("contextoMd deve ser um caminho relativo a docs/ terminando em .md");
    }

    public Projeto comId(long novoId) {
        return new Projeto(novoId, squadId, slug, nome, descricao, contextoMd, arquivado);
    }
}
