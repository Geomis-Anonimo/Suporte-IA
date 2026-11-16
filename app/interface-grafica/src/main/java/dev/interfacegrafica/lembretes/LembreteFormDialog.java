package dev.interfacegrafica.lembretes;

import atlantafx.base.theme.Styles;
import dev.interfacegrafica.client.ApiException;
import dev.interfacegrafica.client.ApiOfflineException;
import dev.interfacegrafica.client.dto.LembreteRequest;
import dev.interfacegrafica.client.dto.LembreteResponse;
import dev.interfacegrafica.client.dto.ParticipanteDto;
import dev.interfacegrafica.client.dto.RecorrenciaDto.Tipo;
import dev.interfacegrafica.config.AppConfig;
import dev.interfacegrafica.lembretes.LembreteFormValidator.Campo;
import dev.interfacegrafica.ui.Confirmacao;
import dev.interfacegrafica.ui.Estilos;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Formulario de criar/editar/duplicar. Salva de forma assincrona e so fecha no sucesso. */
public class LembreteFormDialog extends Dialog<LembreteResponse> {

    public enum Modo {
        NOVO("Novo lembrete"), EDITAR("Editar lembrete"), DUPLICAR("Duplicar lembrete");

        final String titulo;

        Modo(String titulo) { this.titulo = titulo; }
    }

    /** Quem efetivamente chama a API (POST ou PUT). */
    @FunctionalInterface
    public interface Salvar {
        void executar(LembreteRequest req, Consumer<LembreteResponse> ok, Consumer<Throwable> falha);
    }

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TextField titulo = new TextField();
    private final TextField descricao = new TextField();
    private final ToggleGroup grupoTipo = new ToggleGroup();
    private final DatePicker data = new DatePicker();
    private final TextField hora = new TextField();
    private final ComboBox<DayOfWeek> diaSemana = new ComboBox<>();
    private final TextField diaMes = numero(2);
    private final TextField antecedencia = numero(4);
    private final TextArea mensagem = new TextArea();
    private final Label contador = new Label();
    private final TextField contexto = new TextField();
    private final VBox linhasParticipantes = new VBox(6);
    private final List<LinhaParticipante> participantes = new ArrayList<>();
    private final Label bannerErro = new Label();
    private final Label dicaDiaMes = dica("Meses sem esse dia são pulados.");

    private final Map<Campo, Control> controles = new EnumMap<>(Campo.class);
    private final Map<Campo, Label> errosCampo = new EnumMap<>(Campo.class);
    private final Set<Campo> tocados = EnumSet.noneOf(Campo.class);
    private final BooleanProperty valido = new SimpleBooleanProperty(false);
    private final BooleanProperty salvando = new SimpleBooleanProperty(false);
    private final Button botaoSalvar;
    private final LembreteForm inicial;
    private boolean concluido;
    private boolean preenchendo;

    public LembreteFormDialog(Window dono, Modo modo, LembreteForm form, Salvar salvar) {
        initOwner(dono);
        setTitle(modo.titulo);
        setHeaderText(modo.titulo);
        setResizable(true);

        DialogPane pane = getDialogPane();
        pane.getStylesheets().add(Estilos.css());
        pane.getStyleClass().add("form-lembrete");
        ButtonType tipoSalvar = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        ButtonType tipoCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(tipoCancelar, tipoSalvar);

        VBox corpo = montarCorpo();
        corpo.disableProperty().bind(salvando);
        ScrollPane rolagem = new ScrollPane(corpo);
        rolagem.setFitToWidth(true);
        rolagem.getStyleClass().add("edge-to-edge");
        rolagem.setPrefViewportHeight(560);
        pane.setContent(rolagem);
        pane.setPrefWidth(660);

        preencher(form);
        inicial = ler();
        if (modo != Modo.NOVO) tocados.addAll(EnumSet.allOf(Campo.class));
        ligarValidacao();
        revalidar();

        botaoSalvar = (Button) pane.lookupButton(tipoSalvar);
        botaoSalvar.getStyleClass().add(Styles.ACCENT);
        botaoSalvar.disableProperty().bind(valido.not().or(salvando));
        botaoSalvar.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            enviar(salvar);
        });

        setResultConverter(b -> null); // o resultado e definido em enviar()
        setOnCloseRequest(e -> {
            if (concluido) return;
            if (salvando.get()) {
                e.consume();
                return;
            }
            if (!ler().equals(inicial) && !Confirmacao.confirmar(pane.getScene().getWindow(), "Descartar alterações?",
                    "Há alterações não salvas. Fechar mesmo assim?", "Descartar", true)) {
                e.consume();
            }
        });
        setOnShown(e -> Platform.runLater(titulo::requestFocus));
    }

    // ---------------------------------------------------------------- layout

    private VBox montarCorpo() {
        bannerErro.getStyleClass().add("banner-erro");
        bannerErro.setWrapText(true);
        bannerErro.setMaxWidth(Double.MAX_VALUE);
        mostrar(bannerErro, false);

        titulo.setPromptText("Ex.: Daily do time");
        descricao.setPromptText("Resumo curto (opcional)");

        // recorrencia: seletor segmentado
        HBox pilulas = new HBox();
        String[] estilos = {Styles.LEFT_PILL, Styles.CENTER_PILL, Styles.CENTER_PILL, Styles.RIGHT_PILL};
        String[] rotulos = {"Único", "Diário", "Semanal", "Mensal"};
        Tipo[] tipos = Tipo.values();
        for (int i = 0; i < tipos.length; i++) {
            ToggleButton b = new ToggleButton(rotulos[i]);
            b.getStyleClass().add(estilos[i]);
            b.setUserData(tipos[i]);
            b.setToggleGroup(grupoTipo);
            pilulas.getChildren().add(b);
        }
        grupoTipo.selectedToggleProperty().addListener((obs, antigo, novo) -> {
            if (novo == null && antigo != null) grupoTipo.selectToggle(antigo);
        });

        data.setConverter(conversorData());
        data.setPromptText("dd/mm/aaaa");
        data.setPrefWidth(170);
        hora.setPromptText("HH:mm");
        hora.setPrefColumnCount(5);
        hora.setTextFormatter(new TextFormatter<String>(filtro("[0-9:]{0,5}")));
        diaSemana.getItems().setAll(DayOfWeek.values());
        diaSemana.setConverter(new StringConverter<>() {
            @Override public String toString(DayOfWeek d) { return d == null ? "" : RecorrenciaFormatter.nomeDiaCompleto(d); }
            @Override public DayOfWeek fromString(String s) { return null; }
        });
        diaSemana.setPromptText("Escolha");
        diaSemana.setPrefWidth(170);
        diaMes.setPromptText("1–31");
        antecedencia.setPromptText("5");

        VBox campoData = campo("Data", data, Campo.DATA);
        VBox campoDiaSemana = campo("Dia da semana", diaSemana, Campo.DIA_SEMANA);
        VBox campoDiaMes = campo("Dia do mês", diaMes, Campo.DIA_MES);
        campoDiaMes.getChildren().add(dicaDiaMes);
        VBox campoHora = campo("Hora", hora, Campo.HORA);
        VBox campoAntecedencia = campo("Avisar antes (min)", antecedencia, Campo.ANTECEDENCIA);
        HBox linhaQuando = new HBox(16, campoData, campoDiaSemana, campoDiaMes, campoHora, campoAntecedencia);

        Runnable porTipo = () -> {
            Tipo t = tipoAtual();
            mostrar(campoData, t == Tipo.UNICA);
            mostrar(campoDiaSemana, t == Tipo.SEMANAL);
            mostrar(campoDiaMes, t == Tipo.MENSAL);
            atualizarDicaDiaMes();
        };
        grupoTipo.selectedToggleProperty().addListener((obs, a, b) -> porTipo.run());

        // mensagem
        mensagem.setWrapText(true);
        mensagem.setPrefRowCount(6);
        mensagem.setPromptText("Texto em linguagem natural que vai no Discord");
        contador.getStyleClass().add("dica");
        mensagem.textProperty().addListener((obs, a, b) -> contador.setText(b.length() + " caracteres"));
        HBox rodapeMensagem = new HBox(dica("Texto que vai no Discord. Se vazio, usa a descrição."), espaco(), contador);

        contexto.setPromptText("time-x/projects/projeto/README.md");

        // participantes
        Button adicionar = new Button("Adicionar participante", new FontIcon(Feather.USER_PLUS));
        adicionar.getStyleClass().addAll(Styles.FLAT, Styles.SMALL);
        adicionar.setOnAction(e -> {
            LinhaParticipante l = adicionarParticipante("", "");
            l.nome.requestFocus();
        });

        VBox corpo = new VBox(14,
                bannerErro,
                campo("Título *", titulo, Campo.TITULO),
                campo("Descrição", descricao, null),
                secao("Quando"),
                pilulas,
                linhaQuando,
                secao("Aviso"),
                new VBox(4, rotulo("Mensagem do aviso"), mensagem, rodapeMensagem),
                new VBox(4, rotulo("Contexto (.md)"), contexto, dica("Caminho relativo a docs/.")),
                secao("Participantes"),
                linhasParticipantes,
                adicionar);
        corpo.getStyleClass().add("form-corpo");
        porTipo.run();
        return corpo;
    }

    private VBox campo(String rotulo, Control controle, Campo campo) {
        VBox caixa = new VBox(4, rotulo(rotulo), controle);
        caixa.getStyleClass().add("campo");
        if (campo != null) {
            Label erro = new Label();
            erro.getStyleClass().add("erro-campo");
            erro.setWrapText(true);
            mostrar(erro, false);
            caixa.getChildren().add(erro);
            controles.put(campo, controle);
            errosCampo.put(campo, erro);
        }
        if (controle instanceof TextField tf && !(controle == hora || controle == diaMes || controle == antecedencia)) {
            tf.setMaxWidth(Double.MAX_VALUE);
        }
        return caixa;
    }

    private LinhaParticipante adicionarParticipante(String nome, String detalhe) {
        LinhaParticipante l = new LinhaParticipante(nome, detalhe);
        participantes.add(l);
        linhasParticipantes.getChildren().add(l.caixa);
        l.nome.textProperty().addListener((obs, a, b) -> revalidar());
        l.detalhe.textProperty().addListener((obs, a, b) -> revalidar());
        if (!preenchendo) revalidar();
        return l;
    }

    private final class LinhaParticipante {
        final TextField nome = new TextField();
        final TextField detalhe = new TextField();
        final HBox caixa = new HBox(8);

        LinhaParticipante(String n, String d) {
            nome.setText(n);
            nome.setPromptText("Nome");
            detalhe.setText(d);
            detalhe.setPromptText("Detalhe (ex.: empresa X, gerente)");
            HBox.setHgrow(nome, Priority.ALWAYS);
            HBox.setHgrow(detalhe, Priority.ALWAYS);
            Button remover = new Button(null, new FontIcon(Feather.X));
            remover.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
            remover.setTooltip(new Tooltip("Remover participante"));
            remover.setOnAction(e -> {
                participantes.remove(this);
                linhasParticipantes.getChildren().remove(caixa);
                revalidar();
            });
            caixa.getChildren().addAll(nome, detalhe, remover);
            caixa.setAlignment(Pos.CENTER_LEFT);
        }
    }

    // ---------------------------------------------------------------- dados

    private void preencher(LembreteForm f) {
        preenchendo = true;
        titulo.setText(f.titulo());
        descricao.setText(f.descricao());
        mensagem.setText(f.mensagem());
        contador.setText(f.mensagem().length() + " caracteres");
        grupoTipo.getToggles().stream().filter(t -> t.getUserData() == f.tipo()).findFirst().ifPresent(grupoTipo::selectToggle);
        data.setValue(f.data());
        hora.setText(f.hora());
        diaSemana.setValue(f.diaSemana());
        diaMes.setText(f.diaMes() == null ? "" : String.valueOf(f.diaMes()));
        antecedencia.setText(f.antecedenciaMin() == null ? "" : String.valueOf(f.antecedenciaMin()));
        contexto.setText(f.contextoMd());
        for (ParticipanteDto p : f.participantes()) adicionarParticipante(p.nome(), p.detalhe());
        preenchendo = false;
    }

    private LembreteForm ler() {
        return new LembreteForm(titulo.getText(), descricao.getText(), mensagem.getText(), tipoAtual(), dataAtual(),
                hora.getText(), diaSemana.getValue(), inteiro(diaMes.getText()), inteiro(antecedencia.getText()),
                contexto.getText(), participantes.stream().map(l -> new ParticipanteDto(l.nome.getText(), l.detalhe.getText())).toList());
    }

    private Tipo tipoAtual() {
        return grupoTipo.getSelectedToggle() == null ? Tipo.UNICA : (Tipo) grupoTipo.getSelectedToggle().getUserData();
    }

    /** Le o texto digitado no DatePicker (mesmo sem Enter); invalido = null. */
    private LocalDate dataAtual() {
        String texto = data.getEditor().getText();
        if (texto == null || texto.isBlank()) return null;
        try {
            return LocalDate.parse(texto.trim(), DATA);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- validacao

    private void ligarValidacao() {
        tocarAoMudar(Campo.TITULO, titulo.textProperty());
        tocarAoMudar(Campo.DATA, data.getEditor().textProperty());
        tocarAoMudar(Campo.DATA, data.valueProperty());
        tocarAoMudar(Campo.HORA, hora.textProperty());
        tocarAoMudar(Campo.DIA_SEMANA, diaSemana.valueProperty());
        tocarAoMudar(Campo.DIA_MES, diaMes.textProperty());
        tocarAoMudar(Campo.ANTECEDENCIA, antecedencia.textProperty());
        for (ObservableValue<?> v : List.of(descricao.textProperty(), mensagem.textProperty(), contexto.textProperty(),
                grupoTipo.selectedToggleProperty())) {
            v.addListener((obs, a, b) -> revalidar());
        }
        controles.forEach((campo, controle) -> controle.focusedProperty().addListener((obs, a, foco) -> {
            if (!foco) {
                tocados.add(campo);
                revalidar();
            }
        }));
    }

    private void tocarAoMudar(Campo campo, ObservableValue<?> valor) {
        valor.addListener((obs, a, b) -> {
            if (!preenchendo) tocados.add(campo);
            revalidar();
        });
    }

    private void revalidar() {
        if (preenchendo) return;
        Map<Campo, String> erros = LembreteFormValidator.validar(ler(), LocalDateTime.now(AppConfig.FUSO));
        valido.set(erros.isEmpty());
        for (Campo c : errosCampo.keySet()) {
            String msg = tocados.contains(c) ? erros.get(c) : null;
            Label l = errosCampo.get(c);
            l.setText(msg == null ? "" : msg);
            mostrar(l, msg != null);
            controles.get(c).pseudoClassStateChanged(Styles.STATE_DANGER, msg != null);
        }
        atualizarDicaDiaMes();
    }

    private void atualizarDicaDiaMes() {
        Integer d = inteiro(diaMes.getText());
        mostrar(dicaDiaMes, tipoAtual() == Tipo.MENSAL && d != null && d > 28 && d <= 31);
    }

    // ---------------------------------------------------------------- salvar

    private void enviar(Salvar salvar) {
        tocados.addAll(EnumSet.allOf(Campo.class));
        revalidar();
        if (!valido.get()) return;
        LembreteRequest req = LembreteFormValidator.paraRequest(ler());
        mostrar(bannerErro, false);
        salvando.set(true);
        botaoSalvar.setText("Salvando…");
        salvar.executar(req, salvo -> {
            salvando.set(false);
            concluido = true;
            setResult(salvo);
            close();
        }, erro -> {
            salvando.set(false);
            botaoSalvar.setText("Salvar");
            bannerErro.setText(mensagemDeErro(erro));
            mostrar(bannerErro, true);
        });
    }

    private static String mensagemDeErro(Throwable erro) {
        if (erro instanceof ApiOfflineException) {
            return "API fora do ar. Rode \"docker compose up -d\" na pasta CLAUDE e tente de novo.";
        }
        if (erro instanceof ApiException api) {
            if (api.status() == 404) return "Esse lembrete não existe mais (foi removido). Feche e atualize a lista.";
            if (api.status() == 400) return "A API recusou: " + api.getMessage();
            return (api.status() > 0 ? "Erro " + api.status() + ": " : "") + api.getMessage();
        }
        return "Erro inesperado: " + erro.getMessage();
    }

    // ---------------------------------------------------------------- utilidades

    private static TextField numero(int digitos) {
        TextField tf = new TextField();
        tf.setPrefColumnCount(Math.max(4, digitos + 1));
        tf.setTextFormatter(new TextFormatter<String>(filtro("\\d{0," + digitos + "}")));
        return tf;
    }

    private static UnaryOperator<TextFormatter.Change> filtro(String regex) {
        return c -> c.getControlNewText().matches(regex) ? c : null;
    }

    private static Integer inteiro(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static StringConverter<LocalDate> conversorData() {
        return new StringConverter<>() {
            @Override public String toString(LocalDate d) { return d == null ? "" : d.format(DATA); }

            @Override public LocalDate fromString(String s) {
                if (s == null || s.isBlank()) return null;
                try {
                    return LocalDate.parse(s.trim(), DATA);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        };
    }

    private static Label rotulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("rotulo");
        return l;
    }

    private static Label secao(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("secao");
        return l;
    }

    private static Label dica(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("dica");
        l.setWrapText(true);
        return l;
    }

    private static Node espaco() {
        javafx.scene.layout.Region r = new javafx.scene.layout.Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static void mostrar(Node n, boolean visivel) {
        n.setVisible(visivel);
        n.setManaged(visivel);
    }
}
