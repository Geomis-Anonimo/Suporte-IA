package dev.api.lembretes.core.domain;

public enum StatusDisparo {
    ENVIADO,
    /** Passou da tolerancia (ex.: PC desligado) e o aviso foi descartado. */
    EXPIRADO
}
