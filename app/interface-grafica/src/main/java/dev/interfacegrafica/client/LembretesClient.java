package dev.interfacegrafica.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import dev.interfacegrafica.client.dto.LembreteRequest;
import dev.interfacegrafica.client.dto.LembreteResponse;
import java.util.List;

/** Cliente sincrono das rotas /api/lembretes da api. Nao conhece JavaFX: quem chama decide em que thread roda. */
public class LembretesClient extends ApiHttp {
    private static final String ROTA = "/api/lembretes";

    public LembretesClient(String baseUrl) {
        super(baseUrl);
    }

    /** @param ativo true = so ativos, false = so inativos, null = todos */
    public List<LembreteResponse> listar(Boolean ativo) {
        return get(ROTA + query("ativo", ativo), new TypeReference<List<LembreteResponse>>() { });
    }

    public LembreteResponse obter(long id) {
        return get(ROTA + "/" + id, new TypeReference<LembreteResponse>() { });
    }

    public LembreteResponse criar(LembreteRequest req) {
        return post(ROTA, req, new TypeReference<LembreteResponse>() { });
    }

    public LembreteResponse atualizar(long id, LembreteRequest req) {
        return put(ROTA + "/" + id, req, new TypeReference<LembreteResponse>() { });
    }

    public void excluir(long id) {
        delete(ROTA + "/" + id);
    }

    /** Envia o aviso agora no Discord. Devolve o texto enviado. */
    public String testar(long id) {
        JsonNode r = post(ROTA + "/" + id + "/testar", null, new TypeReference<JsonNode>() { });
        return r == null ? "" : r.path("mensagemEnviada").asText("");
    }
}
