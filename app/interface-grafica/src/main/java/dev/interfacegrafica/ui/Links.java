package dev.interfacegrafica.ui;

import dev.interfacegrafica.config.AppConfig;
import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Abre URLs no navegador e caminhos .md (relativos a docs/) no programa padrao do sistema. */
public final class Links {
    private static final Logger LOG = Logger.getLogger(Links.class.getName());

    private Links() { }

    /** Abre fora da FX thread. Em erro chama {@code aoFalhar} (na FX thread) com a mensagem. */
    public static void abrir(String link, Consumer<String> aoFalhar) {
        if (link == null || link.isBlank()) return;
        String alvo = link.strip();
        Async.executar(() -> {
            if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Abrir links não é suportado neste sistema");
            if (alvo.matches("(?i)^https?://.*")) {
                Desktop.getDesktop().browse(URI.create(alvo));
            } else {
                Path docs = AppConfig.docsDir();
                if (docs == null) throw new IllegalStateException("Pasta docs/ não encontrada (defina CLAUDE_DOCS_DIR)");
                File arquivo = docs.resolve(alvo).normalize().toFile();
                if (!arquivo.toPath().startsWith(docs) || !Files.exists(arquivo.toPath()))
                    throw new IllegalStateException("Arquivo não encontrado: docs/" + alvo);
                Desktop.getDesktop().open(arquivo);
            }
            return Boolean.TRUE;
        }, ok -> { }, erro -> {
            LOG.log(Level.WARNING, "Falha ao abrir " + alvo, erro);
            aoFalhar.accept(erro.getMessage() == null ? "Não foi possível abrir o link" : erro.getMessage());
        });
    }
}
