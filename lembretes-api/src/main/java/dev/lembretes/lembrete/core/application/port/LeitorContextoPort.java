package dev.lembretes.lembrete.core.application.port;

import java.util.Optional;

public interface LeitorContextoPort {
    /** Le (e limita o tamanho de) um .md de contexto. Vazio se nao existir/nao puder ler. */
    Optional<String> ler(String caminho);
}
