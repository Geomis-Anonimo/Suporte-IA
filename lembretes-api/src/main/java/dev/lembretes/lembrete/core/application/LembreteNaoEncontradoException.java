package dev.lembretes.lembrete.core.application;

public class LembreteNaoEncontradoException extends RuntimeException {
    public LembreteNaoEncontradoException(long id) { super("Lembrete " + id + " nao encontrado"); }
}
