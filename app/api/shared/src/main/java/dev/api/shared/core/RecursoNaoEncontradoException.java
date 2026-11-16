package dev.api.shared.core;

/** Base para "nao encontrado" de qualquer modulo. O ApiExceptionHandler converte em HTTP 404. */
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagem) { super(mensagem); }
}
