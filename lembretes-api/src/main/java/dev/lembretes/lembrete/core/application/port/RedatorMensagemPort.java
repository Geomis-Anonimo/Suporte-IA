package dev.lembretes.lembrete.core.application.port;

import java.util.Optional;

/** Escreve o aviso em linguagem natural (IA). Vazio = usar o texto padrao. */
public interface RedatorMensagemPort {
    Optional<String> redigir(PedidoRedacao pedido);

    record PedidoRedacao(String titulo, String descricao, long minutosRestantes,
                         String participantes, String caminhoContexto, String trechoContexto) {}
}
