package dev.interfacegrafica.client;

/** A API nao respondeu (container parado, porta fechada ou timeout). */
public class ApiOfflineException extends ApiException {
    public ApiOfflineException(String mensagem, Throwable causa) {
        super(0, mensagem, causa);
    }
}
