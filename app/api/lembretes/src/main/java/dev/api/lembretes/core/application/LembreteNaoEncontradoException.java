package dev.api.lembretes.core.application;

import dev.api.shared.core.RecursoNaoEncontradoException;

public class LembreteNaoEncontradoException extends RecursoNaoEncontradoException {
    public LembreteNaoEncontradoException(long id) { super("Lembrete " + id + " nao encontrado"); }
}
