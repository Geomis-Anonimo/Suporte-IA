# PROMPT: interface-grafica, tela "Lembretes" (CRUD)

> Status: **rascunho para validação**. Não implementar até aprovação.

## 1. Objetivo
Criar uma aplicação **desktop em JavaFX** chamada `interface-grafica` para eu gerenciar (listar, criar, editar, excluir e testar) os lembretes da `lembretes-api` que já existe neste repositório. Hoje só dá para usar a API por HTTP. Quero uma interface simples, moderna e rápida para uso pessoal.

Esta é a **primeira tela** do app. Outras telas vão ser adicionadas depois, então o app precisa ter uma **estrutura de navegação** (menu lateral) em que uma tela nova entra sem mexer nas existentes. Nesta entrega, o menu tem **um item só**: "Lembretes".

## 2. Contexto e restrições do ambiente
- Pasta do projeto: `interface-grafica/`, na raiz `CLAUDE/`, ao lado de `lembretes-api/`. **Não alterar nada em `lembretes-api/`.**
- A API roda em Docker em `http://127.0.0.1:8080` (só localhost, sem autenticação). O app desktop **não** entra no `docker-compose.yml`.
- Máquina: Windows 11, **JDK 19** instalado (também tem 18 e JRE 8), **sem Maven instalado**. Por isso:
  - Usar **Maven com Maven Wrapper** (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`) versionado. Para gerar o wrapper sem Maven local: `docker run --rm -v "${PWD}:/app" -w /app maven:3.9.9-eclipse-temurin-17 mvn -N wrapper:wrapper`.
  - `maven.compiler.release` = **17** (roda no JDK 19 e em qualquer LTS ≥ 17).
- Rodar com `.\mvnw.cmd javafx:run`. Testes com `.\mvnw.cmd test`.

## 3. Stack
| Item | Escolha |
|---|---|
| UI | JavaFX 21 (`org.openjfx:javafx-controls`), plugin `org.openjfx:javafx-maven-plugin` |
| Tema | **AtlantaFX** (`io.github.mkpaz:atlantafx-base`): Primer Light/Dark com alternância na barra superior |
| Ícones | Ikonli (`ikonli-javafx` + pacote Feather) |
| HTTP | `java.net.http.HttpClient` (do próprio JDK), sem Spring |
| JSON | Jackson (`jackson-databind` + `jackson-datatype-jsr310`) |
| Testes | JUnit 5. Servidor HTTP falso com `com.sun.net.httpserver.HttpServer` (do JDK), sem dependência extra |

Fixar versões exatas e estáveis no `pom.xml`. Telas montadas **em código Java** (sem FXML), com estilo num `app.css` próprio, em cima do AtlantaFX.

## 4. Contrato da API (fonte da verdade: `lembretes-api/src/main/java/.../presentation/`)
Base: `http://127.0.0.1:8080/api/lembretes`

| Ação | Chamada | Resposta |
|---|---|---|
| Listar | `GET /api/lembretes` (opcional `?ativo=true\|false`) | `200` + lista de `LembreteResponse` |
| Detalhe | `GET /api/lembretes/{id}` | `200` + `LembreteResponse` |
| Criar | `POST /api/lembretes` + `LembreteRequest` | `201` + `LembreteResponse` |
| Editar | `PUT /api/lembretes/{id}` + `LembreteRequest` (substitui tudo e recalcula a próxima ocorrência) | `200` + `LembreteResponse` |
| Excluir | `DELETE /api/lembretes/{id}` | `204` |
| Testar | `POST /api/lembretes/{id}/testar` (manda o aviso para o Discord agora, sem alterar o lembrete) | `200` + `{"mensagemEnviada": "..."}` |

**LembreteRequest** (POST/PUT):
```json
{
  "titulo": "obrigatório, não vazio",
  "descricao": "opcional",
  "mensagem": "opcional; texto em linguagem natural do aviso",
  "recorrencia": { "tipo": "UNICA|DIARIA|SEMANAL|MENSAL", "dataHora": "2026-11-20T15:00:00", "hora": "09:00", "diaSemana": "THURSDAY", "diaMes": 15 },
  "antecedenciaMin": 5,
  "contextoMd": "opcional; caminho relativo a docs/, ex. time-a/projects/projeto-x/README.md",
  "participantes": [ { "nome": "obrigatório", "detalhe": "opcional" } ]
}
```
- Campos de `recorrencia` por tipo: `UNICA` → `dataHora` (LocalDateTime ISO, sem fuso); `DIARIA` → `hora` (`HH:mm`); `SEMANAL` → `diaSemana` (`MONDAY`…`SUNDAY`) + `hora`; `MENSAL` → `diaMes` (1–31; meses sem esse dia são pulados) + `hora`. **Enviar só os campos do tipo escolhido** (os outros, `null` ou omitidos).
- `antecedenciaMin` ≥ 0; omitido = 5.
- **A API recusa campos desconhecidos** (`fail-on-unknown-properties=true`). O DTO de envio **não pode** levar `id`, `ativo`, `proximaOcorrencia` nem `proximoDisparo`. Use DTOs separados para envio e resposta.

**LembreteResponse**: os campos acima + `id` (long), `proximaOcorrencia` e `proximoDisparo` (ISO com offset, ex. `2026-11-20T15:00:00-03:00`, podem ser `null`), `ativo` (boolean). No cliente, **ignorar campos desconhecidos** na leitura (para não quebrar se a API ganhar campos).

**Erros:** o corpo é sempre `{"erro": "mensagem"}`.
- `400`: validação, data passada ou JSON inválido
- `404`: não encontrado
- `502`: Discord indisponível no `/testar`

Não existe rota para ativar/desativar. `ativo` é **só leitura** (um lembrete `UNICA` fica inativo depois de disparar).

Fuso de exibição: `America/Sao_Paulo`.

## 5. Arquitetura do app
```
interface-grafica/
├── pom.xml, mvnw, mvnw.cmd, .mvn/
├── README.md
└── src/main/java/dev/lembretes/desktop/
    ├── App.java                  # start(): tema, janela, shell
    ├── config/AppConfig.java     # URL base da API (padrão http://127.0.0.1:8080; sobrescrevível por -Dlembretes.api.url ou env LEMBRETES_API_URL)
    ├── client/
    │   ├── LembretesClient.java  # métodos síncronos: listar(Boolean ativo), obter, criar, atualizar, excluir, testar
    │   ├── ApiException.java     # status HTTP + mensagem do {"erro"}; tipo próprio p/ "API fora do ar" (ConnectException)
    │   └── dto/                  # LembreteRequest, LembreteResponse, RecorrenciaDto, ParticipanteDto (records)
    ├── shell/
    │   ├── MainShell.java        # BorderPane: menu lateral (esq.), barra superior, área de conteúdo (centro)
    │   └── Tela.java             # interface: nome(), icone(), Node conteudo(), aoMostrar()
    ├── lembretes/                # a tela desta entrega
    │   ├── LembretesTela.java    # implementa Tela
    │   ├── LembretesViewModel.java  # ObservableList, filtro, estado de carregamento; chama o client via Task
    │   ├── LembreteFormDialog.java  # criar/editar
    │   └── RecorrenciaFormatter.java # recorrência -> texto legível
    └── ui/                       # utilidades: Notificacoes (toasts), Async (roda Task e devolve na FX thread), Confirmacao
```
- Registrar as telas numa lista em `MainShell` (ou `App`). Para adicionar uma tela no futuro basta implementar `Tela` e registrar, sem tocar no resto.
- **Nenhuma chamada HTTP na FX Application Thread.** Tudo via `javafx.concurrent.Task`/executor, com resultado aplicado via `Platform.runLater`/`setOnSucceeded`. Enquanto carrega: indicador de progresso e botões de ação desabilitados.
- O `LembretesClient` não conhece JavaFX (dá para testar isolado).

## 6. Shell (janela principal)
- Janela de 1200×760 (mínimo 900×600), título "Lembretes".
- **Menu lateral** estreito: o item "Lembretes" com ícone de sino, destacado quando ativo.
- **Barra superior:** nome da tela atual. À direita:
  - o **indicador da API**: bolinha verde "API online" ou vermelha "API offline", com o texto "Rode `docker compose up -d` na pasta CLAUDE" no tooltip. É verificado ao abrir o app e a cada 30 s via `GET /api/lembretes?ativo=true`.
  - um botão de **tema claro/escuro**. A preferência é guardada em `java.util.prefs.Preferences`.

## 7. Tela "Lembretes"
### 7.1 Barra de ferramentas
- Busca por texto, com filtro local em título, descrição, mensagem, participantes e `contextoMd` (sem diferenciar maiúsculas nem acentos).
- Filtro de status: `Ativos` (padrão), `Inativos`, `Todos`. Mapeia para `?ativo=true`, `?ativo=false` ou sem parâmetro e **recarrega da API**.
- Botões: **Novo** (destaque), **Editar**, **Duplicar**, **Testar no Discord**, **Excluir**, **Atualizar**. Os que dependem de seleção ficam desabilitados sem seleção.
- Atalhos: `Ctrl+N` (novo), `Enter` ou duplo-clique (editar), `Delete` (excluir), `F5` (atualizar), `Ctrl+F` (focar a busca).

### 7.2 Tabela (TableView)
| Coluna | Conteúdo |
|---|---|
| Título | `titulo` em negrito; abaixo, em texto menor e cinza, a `descricao` (se houver) |
| Quando | texto do `RecorrenciaFormatter`: "Único · 20/11/2026 15:00", "Todo dia · 09:00", "Toda quinta · 09:00", "Todo dia 15 · 09:00" |
| Próximo aviso | `proximoDisparo` como `dd/MM HH:mm` + relativo ("em 2 h", "amanhã", "em 3 dias"); "—" se for `null` |
| Antecedência | "5 min" |
| Participantes | nomes separados por vírgula (truncado, com tooltip completo) |
| Status | badge "Ativo" (verde) / "Inativo" (cinza) |

- Ordenação padrão: `proximoDisparo` crescente (nulos no fim). Todas as colunas são ordenáveis.
- Linhas inativas aparecem com opacidade reduzida.
- Estados vazios: "Nenhum lembrete ainda. Clique em Novo." quando não há lembretes; "Nada encontrado para essa busca." quando a busca não retorna nada; e, com a API offline, uma mensagem com botão "Tentar de novo".
- **Painel de detalhes** à direita (SplitPane, recolhível), com o lembrete selecionado: a `mensagem` completa (quebra de linha, selecionável para copiar), participantes com detalhe, `contextoMd`, próxima ocorrência e próximo aviso.

### 7.3 Formulário (dialog modal para criar, editar e duplicar)
Título do dialog: "Novo lembrete", "Editar lembrete" ou "Duplicar lembrete".

Campos:
1. **Título*** (TextField).
2. **Descrição** (TextField).
3. **Recorrência***: um seletor segmentado (ToggleButtons) `Único | Diário | Semanal | Mensal`. Conforme a escolha, aparecem **só** os campos daquele tipo:
   - Único: DatePicker + hora.
   - Diário: hora.
   - Semanal: dia da semana (ComboBox "Segunda"…"Domingo", mapeado para `MONDAY`…) + hora.
   - Mensal: dia do mês (Spinner 1–31) + hora. Com o dia > 28, mostrar a dica "Meses sem esse dia são pulados".

   A hora é um campo `HH:mm` com máscara/validação (ou Spinners de hora/minuto).
4. **Antecedência (min)**: Spinner ≥ 0, padrão 5.
5. **Mensagem do aviso**: TextArea grande, com o contador de caracteres e a dica "Texto que vai no Discord. Se vazio, usa a descrição."
6. **Contexto (.md)**: TextField com prompt `time-x/projects/projeto/README.md` e a dica "Caminho relativo a docs/".
7. **Participantes**: uma lista editável (nome* + detalhe) com botões adicionar e remover. Linhas sem nome são descartadas ao salvar.

Comportamento:
- **Validação no cliente antes de enviar:** título obrigatório, campos obrigatórios do tipo de recorrência e, no tipo `UNICA`, data/hora no futuro. Erros aparecem inline, abaixo do campo (borda vermelha + texto), e o botão **Salvar** fica desabilitado enquanto houver erro.
- Salvar: `POST` (novo/duplicar) ou `PUT` (editar). Durante o envio o botão mostra o progresso e o dialog fica bloqueado. Se a API devolver `400`, mostrar a mensagem do `{"erro"}` no topo do dialog **sem fechá-lo e sem perder o que foi digitado**.
- No sucesso: fechar o dialog, atualizar a linha (ou inserir e selecionar a nova) e mostrar o toast "Lembrete salvo. Próximo aviso: 20/11 14:55".
- **Duplicar:** abre o formulário preenchido com os dados do lembrete selecionado e o título "Cópia de …". Salva como novo.
- `Esc` cancela. Se houver alteração não salva, pedir confirmação ao fechar.

### 7.4 Excluir
- Confirmação: "Excluir o lembrete '<título>'? Essa ação não pode ser desfeita." O botão de confirmar é vermelho.
- Depois de `204`: remover a linha da tabela e mostrar um toast. Se der `404`, avisar "Já tinha sido removido" e recarregar a lista.

### 7.5 Testar no Discord
- Confirmação curta: "Enviar o aviso de '<título>' agora no Discord?"
- No sucesso: toast "Aviso enviado", com a opção de ver o texto enviado (`mensagemEnviada`) num popup. Se der `502`: toast de erro "Discord indisponível: <erro>".

## 8. Tratamento de erros (global)
- API offline (`ConnectException` ou timeout): o indicador fica vermelho, aparece o estado vazio de offline e um toast "API fora do ar. Rode `docker compose up -d` na pasta CLAUDE."
- `404` em editar/testar: toast + recarregar a lista.
- Qualquer outro erro: toast com status + mensagem. Detalhes no log (`java.util.logging`), nunca stack trace na tela.
- Timeout do HttpClient: 5 s para conectar e 10 s por requisição.

## 9. Visual
- Seguir o AtlantaFX (Primer): espaçamentos generosos, cantos arredondados, botão principal com a cor de destaque, botão de perigo vermelho.
- Fonte do sistema, em tamanho 13–14.
- Toasts no canto inferior direito, que somem em 4 s (erros ficam até serem fechados).
- O modo claro e o escuro precisam ficar legíveis. **Não usar cores fixas no CSS**: usar as variáveis do tema AtlantaFX (`-color-fg-default`, `-color-accent-emphasis`, `-color-success-fg` etc.).

## 10. Testes
- `LembretesClientTest`: sobe um `HttpServer` falso e cobre:
  - listar com e sem filtro
  - criar (confere o JSON enviado: sem `id`/`ativo`, só os campos do tipo de recorrência)
  - editar e excluir (`204`)
  - testar
  - parse de `{"erro"}` em 400/404/502
  - API offline
- `RecorrenciaFormatterTest`: os 4 tipos + nomes de dia em português.
- Validação do formulário extraída numa classe pura (`LembreteFormValidator`) e testada sem JavaFX.
- `.\mvnw.cmd test` precisa passar.

## 11. Entregáveis
- O projeto `interface-grafica/` completo, compilando e rodando com `.\mvnw.cmd javafx:run`.
- Um `interface-grafica/README.md` com: o que é, os pré-requisitos (JDK 17+, API rodando), como rodar, como testar, como apontar para outra URL e **como adicionar uma tela nova** (passo a passo com a interface `Tela`).
- Atualizar o `lembretes-api/README.md` só na linha "Fora de escopo", tirando "UI" e apontando para o `interface-grafica/`.
- `.gitignore` com `target/`.

## 12. Fora de escopo (nesta entrega)
- Outras telas (só deixar a estrutura pronta para elas).
- Ativar/desativar um lembrete (a API não tem essa rota).
- Instalador/empacotamento (`jpackage`), ícone na bandeja, notificações do sistema operacional.
- Alterações na `lembretes-api`.
- Abrir o arquivo `contextoMd` a partir da tela.

## 13. Critérios de aceite
1. Com a API rodando, abro o app e vejo os lembretes ativos ordenados pelo próximo aviso.
2. Crio um lembrete de cada tipo (único, diário, semanal, mensal) e cada um aparece na lista com o "Próximo aviso" correto.
3. Edito um lembrete, mudo a recorrência de semanal para diária e o próximo aviso é recalculado.
4. Um lembrete único com data no passado é barrado no formulário. Se passar pela validação do cliente, o erro `400` da API aparece no dialog sem perder os dados.
5. Excluo com confirmação e a linha some.
6. "Testar no Discord" envia e mostra o toast; com o Discord fora, aparece o erro `502` de forma amigável.
7. Com o Docker parado, o app abre normalmente, o indicador fica vermelho e a mensagem orienta a subir a API; ao subir e clicar em "Tentar de novo", tudo volta.
8. Alterno entre claro e escuro e a escolha persiste ao reabrir.
9. A tela não trava durante as chamadas à API.
10. `.\mvnw.cmd test` passa.
