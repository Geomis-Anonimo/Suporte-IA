package dev.lembretes.lembrete.core.application.port;

public interface NotificadorPort {
    void enviar(String mensagem) throws NotificacaoException;

    class NotificacaoException extends Exception {
        public NotificacaoException(String mensagem, Throwable causa) { super(mensagem, causa); }
        public NotificacaoException(String mensagem) { super(mensagem); }
    }
}
