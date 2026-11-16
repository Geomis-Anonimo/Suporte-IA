package dev.interfacegrafica.tarefas;

import dev.interfacegrafica.client.ApiException;
import dev.interfacegrafica.client.ApiOfflineException;

/** Mensagens amigaveis para erros da api (formularios e toasts). */
final class Erros {
    static final String DICA_DOCKER = "Rode \"docker compose up -d\" na pasta CLAUDE.";

    private Erros() { }

    static String mensagem(Throwable erro) {
        if (erro instanceof ApiOfflineException) return "API fora do ar. " + DICA_DOCKER;
        if (erro instanceof ApiException api) {
            return switch (api.status()) {
                case 400 -> "A API recusou: " + api.getMessage();
                case 404 -> "Não encontrado (pode ter sido removido): " + api.getMessage();
                case 409 -> api.getMessage();
                case 0 -> api.getMessage();
                default -> "Erro " + api.status() + ": " + api.getMessage();
            };
        }
        return "Erro inesperado: " + erro.getClass().getSimpleName();
    }
}
