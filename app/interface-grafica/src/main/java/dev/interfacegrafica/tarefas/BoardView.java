package dev.interfacegrafica.tarefas;

import atlantafx.base.theme.Styles;
import dev.interfacegrafica.client.dto.TarefasDtos.Status;
import dev.interfacegrafica.client.dto.TarefasDtos.TarefaDto;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Board kanban com 4 colunas de largura igual. Redesenhado a cada mudanca (dezenas de cards: barato). */
public class BoardView extends GridPane {

    /** O que o board pede para a tela fazer. */
    public interface Acoes {
        void selecionar(TarefaDto t);

        void editar(TarefaDto t);

        ContextMenu menu(TarefaDto t);

        void abrirLink(TarefaDto t);

        /** Soltou o card {@code id} na coluna, antes de {@code antesDeId} (null = fim). */
        void mover(long id, Status status, Long antesDeId);

        void criarRapida(String titulo);

        void alternarFeitoTodas();
    }

    private final Acoes acoes;
    private final Map<Status, Coluna> colunas = new EnumMap<>(Status.class);

    public BoardView(Acoes acoes) {
        this.acoes = acoes;
        getStyleClass().add("board");
        setHgap(12);
        setFocusTraversable(true); // recebe o foco ao clicar num card (Delete/Enter/Ctrl+1..4)
        int i = 0;
        for (Status s : Status.values()) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            cc.setFillWidth(true);
            getColumnConstraints().add(cc);
            Coluna c = new Coluna(s);
            colunas.put(s, c);
            add(c, i++, 0);
        }
        RowConstraints rc = new RowConstraints();
        rc.setVgrow(Priority.ALWAYS);
        rc.setFillHeight(true);
        getRowConstraints().add(rc);
    }

    /**
     * @param porColuna      tarefas visiveis de cada coluna, na ordem
     * @param mostrarProjeto mostra o chip do projeto (visao do squad inteiro)
     * @param feitoTodas     a coluna Feito esta mostrando todas (senao, so as recentes)
     */
    public void exibir(Map<Status, List<TarefaDto>> porColuna, Long selecionadaId, boolean mostrarProjeto,
                       boolean feitoTodas, boolean podeCriar) {
        for (Status s : Status.values()) {
            colunas.get(s).exibir(porColuna.getOrDefault(s, List.of()), selecionadaId, mostrarProjeto, feitoTodas, podeCriar);
        }
    }

    // ------------------------------------------------------------- coluna

    private final class Coluna extends VBox {
        private final Status status;
        private final Label contador = new Label();
        private final VBox cartas = new VBox(8);
        private final Region indicador = new Region();
        private final TextField rapida = new TextField();
        private final Hyperlink verTodas = new Hyperlink();

        Coluna(Status status) {
            this.status = status;
            getStyleClass().addAll("coluna-board", "coluna-" + status.name().toLowerCase().replace('_', '-'));
            setSpacing(8);

            Label nome = new Label(status.rotulo);
            nome.getStyleClass().add("titulo-coluna");
            contador.getStyleClass().add("contador-coluna");
            Region espaco = new Region();
            HBox.setHgrow(espaco, Priority.ALWAYS);
            HBox cabecalho = new HBox(8, nome, espaco, contador);
            cabecalho.setAlignment(Pos.CENTER_LEFT);
            cabecalho.getStyleClass().add("cabecalho-coluna");

            cartas.getStyleClass().add("cartas-coluna");
            cartas.setFillWidth(true);
            indicador.getStyleClass().add("indicador-drop");
            indicador.setManaged(true);
            ScrollPane rolagem = new ScrollPane(cartas);
            rolagem.setFitToWidth(true);
            rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            rolagem.getStyleClass().addAll("rolagem-coluna", Styles.FLAT);
            VBox.setVgrow(rolagem, Priority.ALWAYS);
            getChildren().addAll(cabecalho, rolagem);

            if (status == Status.A_FAZER) {
                rapida.setPromptText("+ Adicionar tarefa (Enter)");
                rapida.getStyleClass().add("adicionar-rapido");
                rapida.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ENTER && !rapida.getText().isBlank()) {
                        acoes.criarRapida(rapida.getText().strip());
                        rapida.clear();
                    } else if (e.getCode() == KeyCode.ESCAPE) {
                        rapida.clear();
                    }
                });
                getChildren().add(rapida);
            }
            if (status == Status.FEITO) {
                verTodas.setOnAction(e -> acoes.alternarFeitoTodas());
                verTodas.getStyleClass().add("ver-todas");
                getChildren().add(verTodas);
            }

            setOnDragOver(this::arrastandoSobre);
            setOnDragExited(e -> cartas.getChildren().remove(indicador));
            setOnDragDropped(this::soltou);
        }

        void exibir(List<TarefaDto> tarefas, Long selecionadaId, boolean mostrarProjeto, boolean feitoTodas, boolean podeCriar) {
            contador.setText(String.valueOf(tarefas.size()));
            List<Node> nos = new ArrayList<>();
            for (TarefaDto t : tarefas) {
                CardTarefa card = new CardTarefa(t, mostrarProjeto, acoes);
                card.setSelecionado(t.id().equals(selecionadaId));
                nos.add(card);
            }
            if (nos.isEmpty()) {
                Label vazio = new Label("Nada aqui");
                vazio.getStyleClass().add("coluna-vazia");
                vazio.setMaxWidth(Double.MAX_VALUE);
                nos.add(vazio);
            }
            cartas.getChildren().setAll(nos);
            rapida.setDisable(!podeCriar);
            verTodas.setText(feitoTodas ? "Mostrar só os últimos " + TarefasViewModel.DIAS_FEITO + " dias"
                    : "Ver todas as concluídas");
            verTodas.setGraphic(new FontIcon(feitoTodas ? Feather.FILTER : Feather.LIST));
        }

        private List<CardTarefa> cards() {
            List<CardTarefa> r = new ArrayList<>();
            for (Node n : cartas.getChildren()) {
                if (n instanceof CardTarefa c) r.add(c);
            }
            return r;
        }

        private Long idArrastado(DragEvent e) {
            String s = e.getDragboard().hasString() ? e.getDragboard().getString() : null;
            if (s == null || !s.startsWith(CardTarefa.PREFIXO_DRAG)) return null;
            try {
                return Long.parseLong(s.substring(CardTarefa.PREFIXO_DRAG.length()));
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        /** Indice (entre os cards) onde o card cairia, pela altura do mouse. */
        private int posicao(DragEvent e) {
            Point2D p = cartas.sceneToLocal(e.getSceneX(), e.getSceneY());
            List<CardTarefa> cs = cards();
            for (int i = 0; i < cs.size(); i++) {
                var b = cs.get(i).getBoundsInParent();
                if (p.getY() < b.getMinY() + b.getHeight() / 2) return i;
            }
            return cs.size();
        }

        private void arrastandoSobre(DragEvent e) {
            if (idArrastado(e) == null) return;
            e.acceptTransferModes(TransferMode.MOVE);
            int pos = posicao(e);
            cartas.getChildren().remove(indicador);
            List<CardTarefa> cs = cards();
            int indiceNo = pos < cs.size() ? cartas.getChildren().indexOf(cs.get(pos)) : cartas.getChildren().size();
            cartas.getChildren().add(indiceNo, indicador);
            e.consume();
        }

        private void soltou(DragEvent e) {
            Long id = idArrastado(e);
            cartas.getChildren().remove(indicador);
            if (id == null) return;
            List<CardTarefa> cs = cards();
            int pos = posicao(e);
            Long antesDe = pos < cs.size() ? cs.get(pos).tarefa().id() : null;
            // soltar logo acima de si mesmo (ou no fim, sendo o ultimo) nao muda nada
            int atual = -1;
            for (int i = 0; i < cs.size(); i++) {
                if (cs.get(i).tarefa().id().equals(id)) atual = i;
            }
            boolean semMudanca = atual >= 0 && (pos == atual || pos == atual + 1);
            e.setDropCompleted(true);
            e.consume();
            if (semMudanca) return;
            if (id.equals(antesDe)) return;
            acoes.mover(id, status, antesDe);
        }
    }
}
