package dev.interfacegrafica.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;

/**
 * Configuracao do app.
 * <ul>
 *   <li>URL da api: -Dapi.url ou env API_URL (aceita tambem os nomes antigos lembretes.api.url / LEMBRETES_API_URL).</li>
 *   <li>Pasta docs/ (para abrir links .md): -Dclaude.docs ou env CLAUDE_DOCS_DIR; senao procura subindo a partir
 *       da pasta atual uma pasta docs/ com INDEX.md.</li>
 * </ul>
 */
public final class AppConfig {
    public static final String URL_PADRAO = "http://127.0.0.1:8080";
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private AppConfig() { }

    public static String apiUrl() {
        return primeiro(System.getProperty("api.url"), System.getenv("API_URL"),
                System.getProperty("lembretes.api.url"), System.getenv("LEMBRETES_API_URL"), URL_PADRAO);
    }

    /** Pasta docs/ da base CLAUDE, ou null se nao encontrada. */
    public static Path docsDir() {
        String configurado = primeiro(System.getProperty("claude.docs"), System.getenv("CLAUDE_DOCS_DIR"), null);
        if (configurado != null) return Path.of(configurado).toAbsolutePath().normalize();
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            Path docs = p.resolve("docs");
            if (Files.isRegularFile(docs.resolve("INDEX.md"))) return docs.normalize();
        }
        return null;
    }

    private static String primeiro(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }
}
