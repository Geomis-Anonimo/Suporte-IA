package dev.lembretes.lembrete.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.lembretes.lembrete.core.application.AtualizarLembreteService;
import dev.lembretes.lembrete.core.application.ConsultarLembretesService;
import dev.lembretes.lembrete.core.application.CriarLembreteService;
import dev.lembretes.lembrete.core.application.DispararLembretesVencidosService;
import dev.lembretes.lembrete.core.application.MontadorAviso;
import dev.lembretes.lembrete.core.application.RemoverLembreteService;
import dev.lembretes.lembrete.core.application.TestarLembreteService;
import dev.lembretes.lembrete.core.application.port.DisparoRepository;
import dev.lembretes.lembrete.core.application.port.LeitorContextoPort;
import dev.lembretes.lembrete.core.application.port.LembreteRepository;
import dev.lembretes.lembrete.core.application.port.NotificadorPort;
import dev.lembretes.lembrete.core.application.port.RedatorMensagemPort;
import dev.lembretes.lembrete.core.application.port.RelogioPort;
import dev.lembretes.lembrete.infra.claude.ClaudeRedatorMensagem;
import dev.lembretes.lembrete.infra.discord.DiscordWebhookNotificador;
import dev.lembretes.lembrete.infra.filesystem.FileSystemLeitorContexto;
import dev.lembretes.lembrete.infra.jobs.LembreteScheduler;
import dev.lembretes.lembrete.infra.persistence.JdbcDisparoRepository;
import dev.lembretes.lembrete.infra.persistence.JdbcLembreteRepository;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga as portas do core aos adaptadores. O core nao tem anotacoes do Spring. */
@Configuration
@EnableScheduling
public class LembretesConfig {

    @Bean
    RelogioPort relogio(@Value("${lembretes.fuso}") String fuso) {
        ZoneId zona = ZoneId.of(fuso);
        return new RelogioPort() {
            public Instant agora() { return Instant.now(); }
            public ZoneId fuso() { return zona; }
        };
    }

    @Bean
    LembreteRepository lembreteRepository(JdbcClient jdbc, ObjectMapper json) {
        return new JdbcLembreteRepository(jdbc, json);
    }

    @Bean
    DisparoRepository disparoRepository(JdbcClient jdbc) { return new JdbcDisparoRepository(jdbc); }

    @Bean
    NotificadorPort notificador(@Value("${lembretes.discord.webhook-url}") String url, ObjectMapper json) {
        return new DiscordWebhookNotificador(url, json);
    }

    @Bean
    LeitorContextoPort leitorContexto(@Value("${lembretes.contexto-raiz}") String raiz) {
        return new FileSystemLeitorContexto(Path.of(raiz));
    }

    @Bean
    RedatorMensagemPort redator(@Value("${lembretes.claude.api-key}") String chave,
                                @Value("${lembretes.claude.modelo}") String modelo, ObjectMapper json) {
        return new ClaudeRedatorMensagem(chave, modelo, json);
    }

    @Bean
    MontadorAviso montadorAviso(LeitorContextoPort leitor, RedatorMensagemPort redator) {
        return new MontadorAviso(leitor, redator);
    }

    @Bean
    CriarLembreteService criarLembrete(LembreteRepository repo, RelogioPort relogio,
                                       @Value("${lembretes.antecedencia-padrao-min}") int padrao) {
        return new CriarLembreteService(repo, relogio, padrao);
    }

    @Bean
    AtualizarLembreteService atualizarLembrete(LembreteRepository repo, CriarLembreteService criador) {
        return new AtualizarLembreteService(repo, criador);
    }

    @Bean
    ConsultarLembretesService consultarLembretes(LembreteRepository repo) { return new ConsultarLembretesService(repo); }

    @Bean
    RemoverLembreteService removerLembrete(LembreteRepository repo) { return new RemoverLembreteService(repo); }

    @Bean
    TestarLembreteService testarLembrete(LembreteRepository repo, NotificadorPort notificador,
                                         MontadorAviso montador, RelogioPort relogio) {
        return new TestarLembreteService(repo, notificador, montador, relogio);
    }

    @Bean
    DispararLembretesVencidosService dispararVencidos(LembreteRepository repo, DisparoRepository disparos,
                                                      NotificadorPort notificador, MontadorAviso montador,
                                                      RelogioPort relogio,
                                                      @Value("${lembretes.job.janela-extra-min}") long janela,
                                                      @Value("${lembretes.job.tolerancia-atraso-min}") long tolerancia) {
        return new DispararLembretesVencidosService(repo, disparos, notificador, montador, relogio,
                Duration.ofMinutes(janela), Duration.ofMinutes(tolerancia));
    }

    @Bean
    LembreteScheduler lembreteScheduler(DispararLembretesVencidosService disparar) {
        return new LembreteScheduler(disparar);
    }
}
