package dev.interfacegrafica.client;

/** Erro devolvido pela API (corpo {"erro": "..."}) ou falha de comunicacao. */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public ApiException(int status, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.status = status;
    }

    /** Status HTTP; 0 quando nao houve resposta. */
    public int status() { return status; }
}
