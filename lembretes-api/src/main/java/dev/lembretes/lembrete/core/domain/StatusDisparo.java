package dev.lembretes.lembrete.core.domain;

public enum StatusDisparo {
    ENVIADO,
    /** Passou da tolerancia (ex.: PC desligado) e o aviso foi descartado. */
    EXPIRADO
}
