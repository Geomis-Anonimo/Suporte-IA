package dev.interfacegrafica;

import dev.interfacegrafica.client.LembretesClient;
import dev.interfacegrafica.client.TarefasClient;
import dev.interfacegrafica.tarefas.TarefasTela;
import dev.interfacegrafica.config.AppConfig;
import dev.interfacegrafica.lembretes.LembretesTela;
import dev.interfacegrafica.shell.MainShell;
import dev.interfacegrafica.shell.Tela;
import dev.interfacegrafica.ui.ApiMonitor;
import dev.interfacegrafica.ui.Estilos;
import dev.interfacegrafica.ui.Logo;
import dev.interfacegrafica.ui.Notificacoes;
import dev.interfacegrafica.ui.Tema;
import java.util.List;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Ponto de entrada. Telas novas entram na lista {@code telas}. */
public class App extends Application {

    @Override
    public void start(Stage stage) {
        Tema.aplicar();

        LembretesClient client = new LembretesClient(AppConfig.apiUrl());
        ApiMonitor monitor = new ApiMonitor(client);
        Notificacoes notificacoes = new Notificacoes();

        List<Tela> telas = List.of(
                new LembretesTela(client, monitor, notificacoes),
                new TarefasTela(new TarefasClient(AppConfig.apiUrl()), monitor, notificacoes)
                // novas telas aqui
        );

        MainShell shell = new MainShell(telas, monitor, notificacoes);
        Scene cena = new Scene(shell.raiz(), 1200, 760);
        cena.getStylesheets().add(Estilos.css());

        stage.setTitle("Geomis Technologies - Interface Gráfica");
        stage.getIcons().setAll(Logo.atual().get());
        Logo.atual().addListener((obs, a, nova) -> stage.getIcons().setAll(nova));
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(cena);
        stage.show();

        monitor.iniciar();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
