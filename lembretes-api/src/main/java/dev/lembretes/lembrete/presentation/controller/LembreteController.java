package dev.lembretes.lembrete.presentation.controller;

import dev.lembretes.lembrete.core.application.AtualizarLembreteService;
import dev.lembretes.lembrete.core.application.ConsultarLembretesService;
import dev.lembretes.lembrete.core.application.CriarLembreteService;
import dev.lembretes.lembrete.core.application.RemoverLembreteService;
import dev.lembretes.lembrete.core.application.TestarLembreteService;
import dev.lembretes.lembrete.core.application.port.NotificadorPort.NotificacaoException;
import dev.lembretes.lembrete.core.application.port.RelogioPort;
import dev.lembretes.lembrete.presentation.dto.LembreteRequest;
import dev.lembretes.lembrete.presentation.dto.LembreteResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lembretes")
public class LembreteController {
    private final CriarLembreteService criar;
    private final AtualizarLembreteService atualizar;
    private final ConsultarLembretesService consultar;
    private final RemoverLembreteService remover;
    private final TestarLembreteService testar;
    private final RelogioPort relogio;

    public LembreteController(CriarLembreteService criar, AtualizarLembreteService atualizar,
                              ConsultarLembretesService consultar, RemoverLembreteService remover,
                              TestarLembreteService testar, RelogioPort relogio) {
        this.criar = criar;
        this.atualizar = atualizar;
        this.consultar = consultar;
        this.remover = remover;
        this.testar = testar;
        this.relogio = relogio;
    }

    @PostMapping
    public ResponseEntity<LembreteResponse> criar(@Valid @RequestBody LembreteRequest req) {
        var criado = LembreteResponse.de(criar.executar(req.paraComando()), relogio.fuso());
        return ResponseEntity.created(URI.create("/api/lembretes/" + criado.id())).body(criado);
    }

    @GetMapping
    public List<LembreteResponse> listar(@RequestParam(required = false) Boolean ativo) {
        return consultar.listar(ativo).stream().map(l -> LembreteResponse.de(l, relogio.fuso())).toList();
    }

    @GetMapping("/{id}")
    public LembreteResponse obter(@PathVariable long id) {
        return LembreteResponse.de(consultar.obter(id), relogio.fuso());
    }

    @PutMapping("/{id}")
    public LembreteResponse atualizar(@PathVariable long id, @Valid @RequestBody LembreteRequest req) {
        return LembreteResponse.de(atualizar.executar(id, req.paraComando()), relogio.fuso());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable long id) {
        remover.executar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/testar")
    public Map<String, String> testar(@PathVariable long id) throws NotificacaoException {
        return Map.of("mensagemEnviada", testar.executar(id));
    }
}
