package dev.api.tarefas.presentation.controller;

import dev.api.shared.core.port.RelogioPort;
import dev.api.tarefas.core.application.Dados.FiltroTarefas;
import dev.api.tarefas.core.application.TarefaService;
import dev.api.tarefas.core.domain.Prioridade;
import dev.api.tarefas.core.domain.StatusTarefa;
import dev.api.tarefas.presentation.dto.TarefaDtos.MoverRequest;
import dev.api.tarefas.presentation.dto.TarefaDtos.TarefaRequest;
import dev.api.tarefas.presentation.dto.TarefaDtos.TarefaResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/tarefas")
public class TarefaController {
    private final TarefaService tarefas;
    private final RelogioPort relogio;

    public TarefaController(TarefaService tarefas, RelogioPort relogio) {
        this.tarefas = tarefas;
        this.relogio = relogio;
    }

    /**
     * Filtros combinaveis. {@code projetoId=0} = so as sem projeto; {@code status} pode repetir;
     * {@code concluidasDesde} (ISO com offset) esconde as FEITO mais antigas.
     */
    @GetMapping
    public List<TarefaResponse> listar(@RequestParam(required = false) Long squadId,
                                       @RequestParam(required = false) Long projetoId,
                                       @RequestParam(required = false) List<StatusTarefa> status,
                                       @RequestParam(required = false) Prioridade prioridade,
                                       @RequestParam(required = false) String tag,
                                       @RequestParam(required = false) String busca,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime concluidasDesde) {
        var filtro = new FiltroTarefas(squadId, projetoId, status, prioridade, tag, busca,
                concluidasDesde == null ? null : concluidasDesde.toInstant());
        return tarefas.listar(filtro).stream().map(v -> TarefaResponse.de(v, relogio.fuso())).toList();
    }

    @GetMapping("/{id}")
    public TarefaResponse obter(@PathVariable long id) {
        return TarefaResponse.de(tarefas.obter(id), relogio.fuso());
    }

    @PostMapping
    public ResponseEntity<TarefaResponse> criar(@Valid @RequestBody TarefaRequest req) {
        TarefaResponse criada = TarefaResponse.de(tarefas.criar(req.paraDados()), relogio.fuso());
        return ResponseEntity.created(URI.create("/api/tarefas/" + criada.id())).body(criada);
    }

    @PutMapping("/{id}")
    public TarefaResponse atualizar(@PathVariable long id, @Valid @RequestBody TarefaRequest req) {
        return TarefaResponse.de(tarefas.atualizar(id, req.paraDados()), relogio.fuso());
    }

    @PostMapping("/{id}/mover")
    public TarefaResponse mover(@PathVariable long id, @Valid @RequestBody MoverRequest req) {
        return TarefaResponse.de(tarefas.mover(id, req.paraDados()), relogio.fuso());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable long id) {
        tarefas.remover(id);
        return ResponseEntity.noContent().build();
    }
}
