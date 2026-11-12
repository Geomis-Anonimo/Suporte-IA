package dev.lembretes.lembrete.infra.filesystem;

import dev.lembretes.lembrete.core.application.port.LeitorContextoPort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Le .md de contexto a partir de uma raiz configurada; bloqueia caminhos fora da raiz. */
public class FileSystemLeitorContexto implements LeitorContextoPort {
    private static final int MAX_CHARS = 3000;

    private final Path raiz;

    public FileSystemLeitorContexto(Path raiz) {
        this.raiz = raiz.toAbsolutePath().normalize();
    }

    @Override
    public Optional<String> ler(String caminho) {
        try {
            Path arquivo = raiz.resolve(caminho).normalize();
            if (!arquivo.startsWith(raiz) || !arquivo.toString().toLowerCase().endsWith(".md")
                    || !Files.isRegularFile(arquivo)) return Optional.empty();
            String texto = Files.readString(arquivo, StandardCharsets.UTF_8);
            return Optional.of(texto.length() > MAX_CHARS ? texto.substring(0, MAX_CHARS) : texto);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
