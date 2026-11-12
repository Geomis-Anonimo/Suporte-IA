package dev.lembretes.lembrete.presentation.controller;

import dev.lembretes.lembrete.core.application.DadosInvalidosException;
import dev.lembretes.lembrete.core.application.LembreteNaoEncontradoException;
import dev.lembretes.lembrete.core.application.port.NotificadorPort.NotificacaoException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(LembreteNaoEncontradoException.class)
    ResponseEntity<Map<String, String>> naoEncontrado(LembreteNaoEncontradoException e) {
        return erro(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({DadosInvalidosException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> invalido(RuntimeException e) {
        return erro(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validacao(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage()).reduce((a, b) -> a + "; " + b).orElse("invalido");
        return erro(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> corpoIlegivel(HttpMessageNotReadableException e) {
        return erro(HttpStatus.BAD_REQUEST, "Corpo da requisicao invalido (JSON/formatos de data e hora)");
    }

    @ExceptionHandler(NotificacaoException.class)
    ResponseEntity<Map<String, String>> notificacao(NotificacaoException e) {
        return erro(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> erro(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(Map.of("erro", msg));
    }
}
