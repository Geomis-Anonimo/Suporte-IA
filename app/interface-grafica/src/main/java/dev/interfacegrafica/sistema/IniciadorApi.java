package dev.interfacegrafica.sistema;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Sobe a api pelo Docker, como o usuario faria no terminal: se o Docker nao estiver rodando, abre o Docker Desktop
 * e espera; depois roda {@code docker compose up -d} na pasta CLAUDE e espera a api responder.
 * Bloqueante: chamar fora da FX thread. Nao conhece JavaFX.
 */
public class IniciadorApi {
    static final Duration ESPERA_DOCKER = Duration.ofMinutes(3);
    static final Duration ESPERA_COMPOSE = Duration.ofMinutes(10); // a 1a vez pode compilar a imagem
    static final Duration ESPERA_API = Duration.ofSeconds(90);

    /** Erro com mensagem pronta para mostrar ao usuario. */
    public static class FalhaAoIniciar extends Exception {
        public FalhaAoIniciar(String mensagem) { super(mensagem); }
    }

    private final Path raiz;
    private final BooleanSupplier apiNoAr;

    /**
     * @param raiz    pasta CLAUDE (com docker-compose.yml); null se nao encontrada
     * @param apiNoAr verificacao sincrona se a api ja responde
     */
    public IniciadorApi(Path raiz, BooleanSupplier apiNoAr) {
        this.raiz = raiz;
        this.apiNoAr = apiNoAr;
    }

    /** @param progresso recebe a etapa atual (texto curto), chamado na thread de trabalho */
    public void iniciar(Consumer<String> progresso) throws FalhaAoIniciar, InterruptedException {
        if (raiz == null) {
            throw new FalhaAoIniciar("Não encontrei a pasta CLAUDE (com o docker-compose.yml). "
                    + "Configure a variável de ambiente CLAUDE_DIR com o caminho dela.");
        }
        String docker = comandoDocker();

        progresso.accept("Verificando o Docker…");
        if (!dockerNoAr(docker)) {
            Path desktop = dockerDesktop();
            if (desktop == null) {
                throw new FalhaAoIniciar("O Docker não está rodando. Abra o Docker Desktop e tente de novo.");
            }
            progresso.accept("Abrindo o Docker Desktop…");
            try {
                new ProcessBuilder(desktop.toString()).start();
            } catch (IOException e) {
                throw new FalhaAoIniciar("Não consegui abrir o Docker Desktop: " + e.getMessage());
            }
            if (!esperar(() -> dockerNoAr(docker), ESPERA_DOCKER, Duration.ofSeconds(3))) {
                throw new FalhaAoIniciar("O Docker Desktop não ficou pronto em 3 minutos. Confira se ele abriu e tente de novo.");
            }
        }

        progresso.accept("Subindo o container da API…");
        Resultado r = rodar(raiz, ESPERA_COMPOSE, docker, "compose", "up", "-d");
        if (r.codigo() != 0) {
            throw new FalhaAoIniciar("\"docker compose up -d\" falhou: " + ultimasLinhas(r.saida(), 3));
        }

        progresso.accept("Aguardando a API responder…");
        if (!esperar(apiNoAr, ESPERA_API, Duration.ofSeconds(2))) {
            throw new FalhaAoIniciar("O container subiu, mas a API não respondeu em 90 s. "
                    + "Veja o log com \"docker compose logs api\" na pasta CLAUDE.");
        }
    }

    // ---------------------------------------------------------------- docker

    private static boolean dockerNoAr(String docker) {
        return rodar(null, Duration.ofSeconds(20), docker, "info", "--format", "{{.ServerVersion}}").codigo() == 0;
    }

    /** O docker.exe da instalacao padrao do Docker Desktop, ou "docker" (do PATH). */
    private static String comandoDocker() {
        Path padrao = programFiles().resolve("Docker").resolve("Docker").resolve("resources").resolve("bin").resolve("docker.exe");
        return Files.isRegularFile(padrao) ? padrao.toString() : "docker";
    }

    private static Path dockerDesktop() {
        Path exe = programFiles().resolve("Docker").resolve("Docker").resolve("Docker Desktop.exe");
        return Files.isRegularFile(exe) ? exe : null;
    }

    private static Path programFiles() {
        String pf = System.getenv("ProgramFiles");
        return Path.of(pf == null || pf.isBlank() ? "C:\\Program Files" : pf);
    }

    // ---------------------------------------------------------------- processos

    record Resultado(int codigo, String saida) { }

    /** Roda o comando com tempo limite; codigo -1 se nao deu para rodar ou estourou o tempo. */
    static Resultado rodar(Path pasta, Duration limite, String... comando) {
        ProcessBuilder pb = new ProcessBuilder(comando).redirectErrorStream(true);
        if (pasta != null) pb.directory(pasta.toFile());
        Process processo;
        try {
            processo = pb.start();
        } catch (IOException e) {
            return new Resultado(-1, "não foi possível executar " + comando[0] + ": " + e.getMessage());
        }
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Thread leitor = new Thread(() -> copiar(processo.getInputStream(), saida), "saida-processo");
        leitor.setDaemon(true);
        leitor.start();
        try {
            if (!processo.waitFor(limite.toMillis(), TimeUnit.MILLISECONDS)) {
                processo.destroyForcibly();
                return new Resultado(-1, "tempo esgotado (" + limite.toSeconds() + " s) em: " + String.join(" ", Arrays.asList(comando)));
            }
            leitor.join(2000);
        } catch (InterruptedException e) {
            processo.destroyForcibly();
            Thread.currentThread().interrupt();
            return new Resultado(-1, "interrompido");
        }
        synchronized (saida) {
            return new Resultado(processo.exitValue(), saida.toString(StandardCharsets.UTF_8));
        }
    }

    private static void copiar(InputStream in, ByteArrayOutputStream destino) {
        byte[] buf = new byte[4096];
        try (in) {
            int n;
            while ((n = in.read(buf)) != -1) {
                synchronized (destino) {
                    destino.write(buf, 0, n);
                }
            }
        } catch (IOException ignorado) {
            // processo encerrado
        }
    }

    /** Repete a verificacao ate dar true ou estourar o limite. */
    static boolean esperar(BooleanSupplier condicao, Duration limite, Duration intervalo) throws InterruptedException {
        long fim = System.nanoTime() + limite.toNanos();
        while (true) {
            if (condicao.getAsBoolean()) return true;
            if (System.nanoTime() >= fim) return false;
            Thread.sleep(intervalo.toMillis());
        }
    }

    /** As ultimas {@code n} linhas nao vazias, juntas por " | " (para caber num toast). */
    static String ultimasLinhas(String texto, int n) {
        List<String> linhas = new ArrayList<>();
        for (String l : (texto == null ? "" : texto).split("\\R")) {
            if (!l.isBlank()) linhas.add(l.strip());
        }
        if (linhas.isEmpty()) return "(sem saída)";
        return String.join(" | ", linhas.subList(Math.max(0, linhas.size() - n), linhas.size()));
    }
}
