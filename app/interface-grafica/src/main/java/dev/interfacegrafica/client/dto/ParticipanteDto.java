package dev.interfacegrafica.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParticipanteDto(String nome, String detalhe) {
}
