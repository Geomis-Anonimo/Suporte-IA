package dev.lembretes.lembrete.core.domain;

/** Pessoa presente na reuniao. {@code detalhe} e livre (empresa, cargo...). */
public record Participante(String nome, String detalhe) {
    public Participante {
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("nome do participante e obrigatorio");
    }

    public String descricao() {
        return detalhe == null || detalhe.isBlank() ? nome : nome + " (" + detalhe + ")";
    }
}
