package dev.api.tarefas.presentation.controller;

import dev.api.tarefas.core.application.SquadService;
import dev.api.tarefas.presentation.dto.SquadDtos.SquadRequest;
import dev.api.tarefas.presentation.dto.SquadDtos.SquadResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/squads")
public class SquadController {
    private final SquadService squads;

    public SquadController(SquadService squads) { this.squads = squads; }

    @GetMapping
    public List<SquadResponse> listar(@RequestParam(defaultValue = "false") boolean incluirArquivados) {
        return squads.listar(incluirArquivados).stream().map(SquadResponse::de).toList();
    }

    @GetMapping("/{id}")
    public SquadResponse obter(@PathVariable long id) {
        return SquadResponse.de(squads.obter(id));
    }

    @PostMapping
    public ResponseEntity<SquadResponse> criar(@Valid @RequestBody SquadRequest req) {
        SquadResponse criado = SquadResponse.de(squads.criar(req.paraDados()));
        return ResponseEntity.created(URI.create("/api/squads/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    public SquadResponse atualizar(@PathVariable long id, @Valid @RequestBody SquadRequest req) {
        return SquadResponse.de(squads.atualizar(id, req.paraDados()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable long id) {
        squads.remover(id);
        return ResponseEntity.noContent().build();
    }
}
