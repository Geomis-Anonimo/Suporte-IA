package dev.interfacegrafica.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppConfigTest {

    @Test
    void achaARaizSubindoAPartirDoExecutavel(@TempDir Path tmp) throws Exception {
        Path raiz = tmp.resolve("CLAUDE");
        Files.createDirectories(raiz.resolve("app/api"));
        Files.writeString(raiz.resolve("docker-compose.yml"), "services: {}");
        Path exe = Files.createDirectories(raiz.resolve("Interface Grafica/app"));

        assertEquals(raiz.toAbsolutePath().normalize(), AppConfig.procurarRaizAcima(exe));
    }

    @Test
    void semDockerComposeNaoAcha(@TempDir Path tmp) throws Exception {
        Path pasta = Files.createDirectories(tmp.resolve("x/app/api"));
        assertNull(AppConfig.procurarRaizAcima(pasta));
    }
}
