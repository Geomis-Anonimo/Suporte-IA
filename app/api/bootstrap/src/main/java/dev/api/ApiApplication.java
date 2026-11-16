package dev.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da API. Nao tem regra de negocio: so junta os modulos (shared, lembretes, ...).
 * O component scan a partir de {@code dev.api} encontra o @Configuration de cada modulo.
 */
@SpringBootApplication
public class ApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
