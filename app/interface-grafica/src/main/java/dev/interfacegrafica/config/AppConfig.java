package dev.interfacegrafica.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuracao do app.
 * <ul>
 *   <li>URL da api: -Dapi.url ou env API_URL (aceita tambem os nomes antigos lembretes.api.url / LEMBRETES_API_URL).</li>
 *   <li>Pasta raiz CLAUDE (a que tem o docker-compose.yml): -Dclaude.raiz ou env CLAUDE_DIR; senao procura subindo a
 *       partir da pasta atual e da pasta do proprio app. O executavel gerado pelo empacotar.ps1 ja vem com -Dclaude.raiz.</li>
 *   <li>Pasta docs/ (para abrir links .md): -Dclaude.docs ou env CLAUDE_DOCS_DIR; senao &lt;raiz&gt;/docs; senao procura
 *       subindo uma pasta docs/ com INDEX.md.</li>
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

    /** Pasta raiz CLAUDE (com docker-compose.yml), ou null se nao encontrada. */
    public static Path raizClaude() {
        String configurado = primeiro(System.getProperty("claude.raiz"), System.getenv("CLAUDE_DIR"), null);
        if (configurado != null) {
            Path p = Path.of(configurado).toAbsolutePath().normalize();
            if (ehRaiz(p)) return p;
        }
        for (Path inicio : pontosDePartida()) {
            Path achado = procurarRaizAcima(inicio);
            if (achado != null) return achado;
        }
        return null;
    }

    /** Pasta docs/ da base CLAUDE, ou null se nao encontrada. */
    public static Path docsDir() {
        String configurado = primeiro(System.getProperty("claude.docs"), System.getenv("CLAUDE_DOCS_DIR"), null);
        if (configurado != null) return Path.of(configurado).toAbsolutePath().normalize();
        Path raiz = raizClaude();
        if (raiz != null && Files.isRegularFile(raiz.resolve("docs").resolve("INDEX.md"))) return raiz.resolve("docs");
        for (Path inicio : pontosDePartida()) {
            for (Path p = inicio; p != null; p = p.getParent()) {
                Path docs = p.resolve("docs");
                if (Files.isRegularFile(docs.resolve("INDEX.md"))) return docs.normalize();
            }
        }
        return null;
    }

    /** Sobe a partir de {@code inicio} ate achar uma pasta com docker-compose.yml e app/api. */
    static Path procurarRaizAcima(Path inicio) {
        for (Path p = inicio == null ? null : inicio.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
            if (ehRaiz(p)) return p;
        }
        return null;
    }

    private static boolean ehRaiz(Path p) {
        return Files.isRegularFile(p.resolve("docker-compose.yml")) && Files.isDirectory(p.resolve("app").resolve("api"));
    }

    /** Pasta atual e pasta de onde o app foi carregado (o .jar dentro do executavel ou target/classes). */
    private static List<Path> pontosDePartida() {
        List<Path> lista = new ArrayList<>();
        lista.add(Path.of("").toAbsolutePath());
        try {
            lista.add(Path.of(AppConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        } catch (Exception ignorado) {
            // sem code source (raro): fica so a pasta atual
        }
        return lista;
    }

    private static String primeiro(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }
}
