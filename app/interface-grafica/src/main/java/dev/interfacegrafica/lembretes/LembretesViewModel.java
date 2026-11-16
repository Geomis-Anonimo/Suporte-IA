package dev.interfacegrafica.lembretes;

import dev.interfacegrafica.client.ApiOfflineException;
import dev.interfacegrafica.client.LembretesClient;
import dev.interfacegrafica.client.dto.LembreteRequest;
import dev.interfacegrafica.client.dto.LembreteResponse;
import dev.interfacegrafica.ui.ApiMonitor;
import dev.interfacegrafica.ui.Async;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

/** Estado da tela de lembretes e chamadas a API (sempre fora da FX thread). */
public class LembretesViewModel {

    public enum Filtro {
        ATIVOS("Ativos", true), INATIVOS("Inativos", false), TODOS("Todos", null);

        public final String rotulo;
        public final Boolean ativo;

        Filtro(String rotulo, Boolean ativo) {
            this.rotulo = rotulo;
            this.ativo = ativo;
        }

        boolean aceita(LembreteResponse l) { return ativo == null || ativo == l.ativo(); }
    }

    public enum Estado { CARREGANDO, OK, OFFLINE, ERRO }

    private final LembretesClient client;
    private final ApiMonitor monitor;

    private final ObservableList<LembreteResponse> todos = FXCollections.observableArrayList();
    private final FilteredList<LembreteResponse> filtrados = new FilteredList<>(todos);
    private final StringProperty busca = new SimpleStringProperty("");
    private final ObjectProperty<Filtro> filtro = new SimpleObjectProperty<>(Filtro.ATIVOS);
    private final ObjectProperty<Estado> estado = new SimpleObjectProperty<>(Estado.CARREGANDO);
    private final IntegerProperty pendentes = new SimpleIntegerProperty(0);
    private final BooleanBinding ocupado = pendentes.greaterThan(0);
    private long geracao; // descarta respostas de cargas antigas

    public LembretesViewModel(LembretesClient client, ApiMonitor monitor) {
        this.client = client;
        this.monitor = monitor;
        filtrados.predicateProperty().bind(Bindings.createObjectBinding(
                () -> l -> TextoBusca.corresponde(l, busca.get()), busca));
    }

    public ObservableList<LembreteResponse> todos() { return todos; }
    public FilteredList<LembreteResponse> filtrados() { return filtrados; }
    public StringProperty buscaProperty() { return busca; }
    public ObjectProperty<Filtro> filtroProperty() { return filtro; }
    public ObjectProperty<Estado> estadoProperty() { return estado; }
    public BooleanBinding ocupadoProperty() { return ocupado; }

    /** Recarrega da API conforme o filtro. Em falha, chama {@code aoFalhar} (a lista fica vazia se offline). */
    public void recarregar(Consumer<Throwable> aoFalhar) {
        long minha = ++geracao;
        estado.set(Estado.CARREGANDO);
        Boolean ativo = filtro.get().ativo;
        chamar(() -> client.listar(ativo), lista -> {
            if (minha != geracao) return;
            todos.setAll(lista);
            estado.set(Estado.OK);
        }, erro -> {
            if (minha != geracao) return;
            if (erro instanceof ApiOfflineException) {
                todos.clear();
                estado.set(Estado.OFFLINE);
            } else {
                estado.set(Estado.ERRO);
            }
            aoFalhar.accept(erro);
        });
    }

    /** POST (id nulo) ou PUT. No sucesso a lista local e atualizada. */
    public void salvar(Long id, LembreteRequest req, Consumer<LembreteResponse> ok, Consumer<Throwable> falha) {
        chamar(() -> id == null ? client.criar(req) : client.atualizar(id, req), salvo -> {
            aplicarLocal(salvo);
            ok.accept(salvo);
        }, falha);
    }

    public void excluir(LembreteResponse l, Runnable ok, Consumer<Throwable> falha) {
        chamar(() -> {
            client.excluir(l.id());
            return Boolean.TRUE;
        }, r -> {
            removerLocal(l.id());
            ok.run();
        }, falha);
    }

    public void testar(LembreteResponse l, Consumer<String> ok, Consumer<Throwable> falha) {
        chamar(() -> client.testar(l.id()), ok, falha);
    }

    /** Coloca o lembrete salvo na lista (ou tira, se nao combina com o filtro atual). */
    private void aplicarLocal(LembreteResponse salvo) {
        int i = indice(salvo.id());
        if (!filtro.get().aceita(salvo)) {
            if (i >= 0) todos.remove(i);
        } else if (i >= 0) {
            todos.set(i, salvo);
        } else {
            todos.add(salvo);
        }
        if (estado.get() != Estado.OK) estado.set(Estado.OK);
    }

    private void removerLocal(Long id) {
        int i = indice(id);
        if (i >= 0) todos.remove(i);
    }

    private int indice(Long id) {
        List<LembreteResponse> lista = todos;
        for (int i = 0; i < lista.size(); i++) {
            if (lista.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    private <T> void chamar(Callable<T> trabalho, Consumer<T> ok, Consumer<Throwable> falha) {
        pendentes.set(pendentes.get() + 1);
        Async.executar(trabalho, r -> {
            pendentes.set(pendentes.get() - 1);
            monitor.informarSucesso();
            ok.accept(r);
        }, erro -> {
            pendentes.set(pendentes.get() - 1);
            monitor.informarFalha(erro);
            falha.accept(erro);
        });
    }
}
