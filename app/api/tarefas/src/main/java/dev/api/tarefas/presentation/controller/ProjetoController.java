package dev.api.tarefas.presentation.controller;

import dev.api.tarefas.core.application.ProjetoService;
import dev.api.tarefas.presentation.dto.ProjetoDtos.ProjetoRequest;
import dev.api.tarefas.presentation.dto.ProjetoDtos.ProjetoResponse;
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
@RequestMapping("/api/projetos")
public class ProjetoController {
    private final ProjetoService projetos;

    public ProjetoController(ProjetoService projetos) { this.projetos = projetos; }

    @GetMapping
    public List<ProjetoResponse> listar(@RequestParam(required = false) Long squadId,
                                        @RequestParam(defaultValue = "false") boolean incluirArquivados) {
        return projetos.listar(squadId, incluirArquivados).stream().map(ProjetoResponse::de).toList();
    }

    @GetMapping("/{id}")
    public ProjetoResponse obter(@PathVariable long id) {
        return ProjetoResponse.de(projetos.obter(id));
    }

    @PostMapping
    public ResponseEntity<ProjetoResponse> criar(@Valid @RequestBody ProjetoRequest req) {
        ProjetoResponse criado = ProjetoResponse.de(projetos.criar(req.paraDados()));
        return ResponseEntity.created(URI.create("/api/projetos/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    public ProjetoResponse atualizar(@PathVariable long id, @Valid @RequestBody ProjetoRequest req) {
        return ProjetoResponse.de(projetos.atualizar(id, req.paraDados()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable long id) {
        projetos.remover(id);
        return ResponseEntity.noContent().build();
    }
}
