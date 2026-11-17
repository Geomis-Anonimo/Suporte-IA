package dev.interfacegrafica.ui;

import dev.interfacegrafica.client.ApiOfflineException;
import dev.interfacegrafica.client.LembretesClient;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.util.Duration;

/**
 * Sabe se a API esta no ar. Verifica ao abrir e a cada 30 s; as telas tambem informam o resultado
 * das proprias chamadas (assim o indicador reage na hora).
 */
public class ApiMonitor {
    public enum Status { VERIFICANDO, ONLINE, OFFLINE }

    private final LembretesClient client;
    private final ReadOnlyObjectWrapper<Status> status = new ReadOnlyObjectWrapper<>(Status.VERIFICANDO);
    private Timeline timeline;

    public ApiMonitor(LembretesClient client) {
        this.client = client;
    }

    public ReadOnlyObjectProperty<Status> statusProperty() { return status.getReadOnlyProperty(); }

    public void iniciar() {
        verificar();
        timeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> verificar()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    public void verificar() {
        Async.executar(() -> client.listar(true), ok -> status.set(Status.ONLINE), this::informarFalha);
    }

    /** Verificacao sincrona (bloqueante, fora da FX thread): a api respondeu agora? */
    public boolean respondeAgora() {
        try {
            client.listar(true);
            return true;
        } catch (ApiOfflineException e) {
            return false;
        } catch (Exception e) {
            return true; // respondeu, mesmo que com erro
        }
    }

    /** Uma chamada respondeu (mesmo com erro HTTP): a API esta no ar. */
    public void informarSucesso() { status.set(Status.ONLINE); }

    public void informarFalha(Throwable t) {
        status.set(t instanceof ApiOfflineException ? Status.OFFLINE : Status.ONLINE);
    }
}
