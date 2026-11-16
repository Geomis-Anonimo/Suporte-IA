package dev.api.shared.core;

/** A operacao conflita com o estado atual (ex.: excluir algo que ainda tem filhos, slug repetido). Vira HTTP 409. */
public class ConflitoException extends RuntimeException {
    public ConflitoException(String mensagem) { super(mensagem); }
}
