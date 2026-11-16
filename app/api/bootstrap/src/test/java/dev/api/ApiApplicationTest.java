package dev.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Sobe a aplicacao inteira: confere que os modulos se ligam e que o schema de cada modulo foi aplicado. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/api-teste.db",
        "lembretes.job.intervalo-ms=3600000"
})
class ApiApplicationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void contextoSobeComTodosOsModulos() {
        List<String> tabelas = jdbc.sql("SELECT name FROM sqlite_master WHERE type = 'table'").query(String.class).list();
        assertThat(tabelas).contains("lembrete", "disparo", "squad", "projeto", "tarefa");
    }
}
