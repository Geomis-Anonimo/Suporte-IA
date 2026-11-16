package dev.api.lembretes.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.api.lembretes.core.application.AtualizarLembreteService;
import dev.api.lembretes.core.application.ConsultarLembretesService;
import dev.api.lembretes.core.application.CriarLembreteService;
import dev.api.lembretes.core.application.DispararLembretesVencidosService;
import dev.api.lembretes.core.application.MontadorAviso;
import dev.api.lembretes.core.application.RemoverLembreteService;
import dev.api.lembretes.core.application.TestarLembreteService;
import dev.api.lembretes.core.application.port.DisparoRepository;
import dev.api.lembretes.core.application.port.LembreteRepository;
import dev.api.lembretes.infra.jobs.LembreteScheduler;
import dev.api.lembretes.infra.persistence.JdbcDisparoRepository;
import dev.api.lembretes.infra.persistence.JdbcLembreteRepository;
import dev.api.shared.core.port.NotificadorPort;
import dev.api.shared.core.port.RelogioPort;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Modulo lembretes: liga as portas do core aos adaptadores. O core nao tem anotacoes do Spring.
 * RelogioPort e NotificadorPort vem do modulo shared (SharedConfig).
 */
@Configuration
@EnableScheduling
public class LembretesConfig {

    @Bean
    LembreteRepository lembreteRepository(JdbcClient jdbc, ObjectMapper json) {
        return new JdbcLembreteRepository(jdbc, json);
    }

    @Bean
    DisparoRepository disparoRepository(JdbcClient jdbc) { return new JdbcDisparoRepository(jdbc); }

    @Bean
    MontadorAviso montadorAviso() {
        return new MontadorAviso();
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
