package dev.api.tarefas.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.api.shared.core.port.RelogioPort;
import dev.api.tarefas.core.application.ProjetoService;
import dev.api.tarefas.core.application.SquadService;
import dev.api.tarefas.core.application.TarefaService;
import dev.api.tarefas.core.application.port.ProjetoRepository;
import dev.api.tarefas.core.application.port.SquadRepository;
import dev.api.tarefas.core.application.port.TarefaRepository;
import dev.api.tarefas.infra.persistence.JdbcProjetoRepository;
import dev.api.tarefas.infra.persistence.JdbcSquadRepository;
import dev.api.tarefas.infra.persistence.JdbcTarefaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Modulo tarefas: liga as portas do core aos adaptadores. RelogioPort vem do modulo shared. */
@Configuration
public class TarefasConfig {

    @Bean
    SquadRepository squadRepository(JdbcClient jdbc) { return new JdbcSquadRepository(jdbc); }

    @Bean
    ProjetoRepository projetoRepository(JdbcClient jdbc) { return new JdbcProjetoRepository(jdbc); }

    @Bean
    TarefaRepository tarefaRepository(JdbcClient jdbc, ObjectMapper json) { return new JdbcTarefaRepository(jdbc, json); }

    @Bean
    SquadService squadService(SquadRepository s, ProjetoRepository p, TarefaRepository t) { return new SquadService(s, p, t); }

    @Bean
    ProjetoService projetoService(SquadRepository s, ProjetoRepository p, TarefaRepository t) {
        return new ProjetoService(s, p, t);
    }

    @Bean
    TarefaService tarefaService(SquadRepository s, ProjetoRepository p, TarefaRepository t, RelogioPort relogio) {
        return new TarefaService(s, p, t, relogio);
    }
}
