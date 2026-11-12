package dev.lembretes.lembrete.core.application;

import dev.lembretes.lembrete.core.application.port.LeitorContextoPort;
import dev.lembretes.lembrete.core.application.port.RedatorMensagemPort;
import dev.lembretes.lembrete.core.application.port.RedatorMensagemPort.PedidoRedacao;
import dev.lembretes.lembrete.core.domain.Lembrete;
import dev.lembretes.lembrete.core.domain.Participante;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

/** Monta o texto do aviso: tenta a IA e cai num texto padrao se ela nao estiver disponivel. */
public class MontadorAviso {
    private final LeitorContextoPort leitor;
    private final RedatorMensagemPort redator;

    public MontadorAviso(LeitorContextoPort leitor, RedatorMensagemPort redator) {
        this.leitor = leitor;
        this.redator = redator;
    }

    public String montar(Lembrete l, Instant agora) {
        long minutos = Math.max(0, Math.round(Duration.between(agora, l.proximaOcorrencia()).toSeconds() / 60.0));
        String participantes = l.participantes().stream().map(Participante::descricao).collect(Collectors.joining(", "));
        String trecho = l.contextoMd() == null || l.contextoMd().isBlank()
                ? null : leitor.ler(l.contextoMd()).orElse(null);

        var pedido = new PedidoRedacao(l.titulo(), l.descricao(), minutos, participantes, l.contextoMd(), trecho);
        return redator.redigir(pedido).filter(t -> !t.isBlank()).orElseGet(() -> padrao(pedido));
    }

    static String padrao(PedidoRedacao p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.minutosRestantes() == 0 ? "⏰ Agora" : "⏰ Daqui " + p.minutosRestantes() + " min")
                .append(": **").append(p.titulo()).append("**");
        if (p.descricao() != null && !p.descricao().isBlank()) sb.append("\n").append(p.descricao());
        if (!p.participantes().isBlank()) sb.append("\n👥 Participantes: ").append(p.participantes());
        if (p.caminhoContexto() != null && !p.caminhoContexto().isBlank())
            sb.append("\n📄 Contexto: ").append(p.caminhoContexto());
        if (p.trechoContexto() != null && !p.trechoContexto().isBlank()) {
            String t = p.trechoContexto().strip();
            sb.append("\n> ").append((t.length() > 400 ? t.substring(0, 400) + "…" : t).replace("\n", "\n> "));
        }
        return sb.toString();
    }
}
