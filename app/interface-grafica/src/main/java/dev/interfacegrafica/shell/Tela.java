package dev.interfacegrafica.shell;

import javafx.scene.Node;
import org.kordamp.ikonli.Ikon;

/**
 * Uma tela do app. Para adicionar uma nova: implementar esta interface e registrar a instancia
 * na lista de telas em {@code App.start()}. O shell cuida do menu lateral e da troca de conteudo.
 */
public interface Tela {
    /** Nome curto, exibido no menu lateral e na barra superior. */
    String nome();

    /** Icone do menu lateral (ex.: Feather.BELL). */
    Ikon icone();

    /** Conteudo da tela. Deve devolver sempre o mesmo Node (criado uma vez). */
    Node conteudo();

    /** Chamado toda vez que a tela passa a ser exibida. */
    default void aoMostrar() { }
}
