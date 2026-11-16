package dev.interfacegrafica.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.concurrent.Task;

/** Roda trabalho bloqueante (HTTP) fora da FX thread e devolve o resultado na FX thread. */
public final class Async {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "api-http");
        t.setDaemon(true);
        return t;
    });

    private Async() { }

    public static <T> void executar(Callable<T> trabalho, Consumer<T> sucesso, Consumer<Throwable> falha) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return trabalho.call();
            }
        };
        task.setOnSucceeded(e -> sucesso.accept(task.getValue()));
        task.setOnFailed(e -> falha.accept(task.getException()));
        EXECUTOR.execute(task);
    }
}
