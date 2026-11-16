package dev.api.lembretes.core.application;

import dev.api.lembretes.core.domain.Participante;
import dev.api.lembretes.core.domain.Recorrencia;
import java.util.List;

/** Dados de entrada para criar/atualizar um lembrete. {@code antecedenciaMin} nulo = padrao. */
public record LembreteComando(String titulo, String descricao, String mensagem, Recorrencia recorrencia,
                              Integer antecedenciaMin, String contextoMd, List<Participante> participantes) {}
