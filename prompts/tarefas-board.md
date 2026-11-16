# PROMPT: Board de tarefas (módulo `tarefas` da api + tela "Tarefas" da interface-grafica)

> Status: **implementado em 16/11/2026** (ver §0 para as decisões). Código em `app/api/tarefas` e `app/interface-grafica/.../tarefas`.

## 0. Decisões validadas
- **Backend:** módulo `tarefas` dentro da `app/api`, e não uma API separada nem algo só local.
- **Colunas:** A Fazer / Fazendo / Bloqueado / Feito.
- **Campos da tarefa,** além de título e descrição: prioridade, tags e link.
- **Projeto opcional:** pode haver tarefa só do squad, que aparece no nó "Sem projeto".
- **Substitui o Quadro A Fazer** nas regras do `CLAUDE.md`.
- **Pacote da interface gráfica:** já foi renomeado para `dev.interfacegrafica`.
- **Fora de escopo, confirmado:** responsável, data limite e sprints.

## 1. Objetivo
Criar um **board de tarefas no estilo Azure Boards**, organizado em três níveis:

```
Squad (ex.: Guardian)            ≈ "Feature" do Azure: o time
 └── Projeto (ex.: guardian_whatsapp)
      └── Tarefa (ex.: "Validar template de cobrança na Omne")
```

O board **substitui o antigo "Quadro A Fazer"** (artifact apagado). Ele é o lugar oficial das pendências do Messias, e o Claude cria e move tarefas nele sozinho (ver §8).

São duas entregas, nesta ordem:
1. **Módulo `tarefas` na `app/api`**: domínio, persistência e rotas REST.
2. **Tela "Tarefas" na `app/interface-grafica`**: o board kanban, registrado como uma nova `Tela` no menu lateral.

## 2. Contexto e restrições
- A API é um **monolito modular Maven** (`app/api`): `shared`, `lembretes`, `bootstrap`. Siga o passo a passo "Como criar um módulo novo" do [app/api/README.md](../app/api/README.md).
  - `tarefas` depende **só de `shared`**, nunca de `lembretes`.
  - Arquitetura hexagonal, igual a `lembretes`: `core/` sem Spring, `infra/` e `presentation/`.
  - Tabelas próprias em `tarefas/src/main/resources/db/tarefas-schema.sql`, com `CREATE TABLE IF NOT EXISTS`. O banco é o mesmo `app/api/data/api.db`. **Não alterar as tabelas do lembretes.**
  - `fail-on-unknown-properties=true` vale para todos os módulos: os DTOs de entrada não podem ter campos calculados.
  - Erros sempre no formato `{"erro": "..."}`. Para "não encontrado", estender `RecursoNaoEncontradoException` (vira 404).
  - **Novo no `shared`:** `ConflitoException`, que o `ApiExceptionHandler` converte em **409**.
- A interface gráfica é JavaFX + AtlantaFX (`app/interface-grafica`). Siga o "Como adicionar uma tela nova" do [README dela](../app/interface-grafica/README.md): nada de HTTP na FX thread, `Async`, `Notificacoes`, `ApiMonitor` e CSS só com variáveis `-color-*`.
- Fuso: `America/Sao_Paulo`. Datas na API em ISO-8601 com offset.
- Testes da API via Docker (comando no README da api). Testes do desktop com `.\mvnw.cmd test`.

## 3. Modelo de domínio (módulo `tarefas`)

### Squad
| Campo | Tipo | Regras |
|---|---|---|
| `id` | long | gerado |
| `slug` | texto | obrigatório, único, minúsculas, `[a-z0-9_-]+` (ex.: `guardian`, `tela-web`). É o mesmo nome da pasta `docs/<slug>/` |
| `nome` | texto | obrigatório (ex.: "Guardian") |
| `ordem` | int | posição no menu (padrão: no fim) |
| `arquivado` | bool | padrão `false` |

### Projeto
| Campo | Tipo | Regras |
|---|---|---|
| `id` | long | gerado |
| `squadId` | long | obrigatório, squad existente |
| `slug` | texto | obrigatório, único dentro do squad (ex.: `guardian_whatsapp`): nome da pasta em `docs/<squad>/projects/` |
| `nome` | texto | obrigatório (pode ser igual ao slug) |
| `descricao` | texto | opcional |
| `contextoMd` | texto | opcional; caminho relativo a `docs/` (ex.: `guardian/projects/guardian_whatsapp/README.md`) |
| `arquivado` | bool | padrão `false` |

### Tarefa
| Campo | Tipo | Regras |
|---|---|---|
| `id` | long | gerado |
| `squadId` | long | obrigatório |
| `projetoId` | long | **opcional** (tarefa do squad sem projeto, ex.: "Pedir acesso à VPN"). Se informado, o projeto tem que ser do mesmo squad (senão 400) |
| `titulo` | texto | obrigatório, até 200 caracteres |
| `descricao` | texto | opcional (multilinha) |
| `status` | enum | `A_FAZER` (padrão), `FAZENDO`, `BLOQUEADO`, `FEITO` |
| `motivoBloqueio` | texto | **obrigatório quando `BLOQUEADO`** (ex.: "aguardando acesso do DevOps (Ruan)"); apagado ao sair de `BLOQUEADO` |
| `prioridade` | enum | `ALTA`, `MEDIA` (padrão), `BAIXA` |
| `tags` | lista de texto | opcional; cada tag é minúscula e sem espaços nas pontas, até 30 caracteres, sem repetir (ex.: `gmud`, `bug`, `daily`) |
| `link` | texto | opcional; URL (`http(s)://…`) **ou** caminho relativo a `docs/` (ex.: `guardian/dailies/2026-11-16-daily.md`) |
| `ordem` | double | posição dentro da coluna (squad + status); calculada pela API |
| `criadaEm` / `atualizadaEm` | instante | calculados |
| `concluidaEm` | instante | preenchido ao entrar em `FEITO`, apagado ao sair |

Regras:
- Squad ou projeto **com tarefas não pode ser excluído** (409 `"Squad tem N tarefas; arquive ou mova antes"`). Arquivar é sempre permitido.
- Squad ou projeto arquivado não aparece nas listagens padrão (`?incluirArquivados=true` mostra) e não aceita tarefas novas (400).
- Ao mover uma tarefa para outra coluna ou posição, a API recalcula `ordem` (ver `mover` em §4).

## 4. Rotas REST
Base `/api`. Todas as respostas de erro são `{"erro": "..."}`.

**Squads**
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/squads?incluirArquivados=false` | lista ordenada por `ordem`. Cada item traz `contagem: {A_FAZER, FAZENDO, BLOQUEADO, FEITO}` |
| POST | `/api/squads` | cria `{slug, nome, ordem?}` → 201. Slug repetido → 409 |
| PUT | `/api/squads/{id}` | substitui `{slug, nome, ordem, arquivado}` |
| DELETE | `/api/squads/{id}` | 204; 409 se tiver projetos ou tarefas |

**Projetos**
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/projetos?squadId=&incluirArquivados=false` | lista (com `contagem` por status) |
| POST | `/api/projetos` | cria `{squadId, slug, nome, descricao?, contextoMd?}` → 201 |
| PUT | `/api/projetos/{id}` | substitui (pode trocar de squad só se não tiver tarefas; senão 409) |
| DELETE | `/api/projetos/{id}` | 204; 409 se tiver tarefas |

**Tarefas**
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/tarefas` | filtros opcionais, combináveis: `squadId`, `projetoId` (`0` = sem projeto), `status` (repetível), `prioridade`, `tag`, `busca` (título/descrição/tags, sem diferenciar maiúsculas nem acentos), `concluidasDesde` (ISO; filtra só as `FEITO`). Ordenação: `status`, depois `ordem` |
| GET | `/api/tarefas/{id}` | detalhe |
| POST | `/api/tarefas` | cria `{squadId, projetoId?, titulo, descricao?, status?, motivoBloqueio?, prioridade?, tags?, link?}` → 201; entra no **fim** da coluna |
| PUT | `/api/tarefas/{id}` | substitui os campos editáveis (mesmo corpo do POST). Se mudar `status`, aplica as regras de `concluidaEm`, `motivoBloqueio` e ordem |
| POST | `/api/tarefas/{id}/mover` | `{status, antesDeId?, motivoBloqueio?}`: move para a coluna `status`, imediatamente **antes** da tarefa `antesDeId` (ou no fim se nulo). Usado pelo arrastar e soltar e pelo Claude |
| DELETE | `/api/tarefas/{id}` | 204 |

Resposta da tarefa: todos os campos da §3, mais `squadSlug`, `projetoSlug` e `projetoNome` (nulos se não houver projeto), para a tela e o Claude não precisarem de outra chamada.

Exemplo do Claude criando uma pendência a partir de uma daily:
```json
POST /api/tarefas
{ "squadId": 1, "projetoId": 4, "titulo": "Validar template de cobrança na Omne",
  "descricao": "Combinado na daily: conferir variáveis do template antes da virada.",
  "prioridade": "ALTA", "tags": ["daily", "omne"], "link": "guardian/dailies/2026-11-16-daily.md" }
```

## 5. Tela "Tarefas" (interface-grafica)
Nova classe `TarefasTela implements Tela`, nome "Tarefas", ícone `Feather.TRELLO` (ou `CHECK_SQUARE`), registrada em `App.start()` **depois** de "Lembretes".

### 5.1 Layout
```
┌───────────────┬────────────────────────────────────────────────────────────────────┐
│ SQUADS        │ [🔍 buscar] [Prioridade ▾] [Tag ▾]           [+ Nova tarefa] [⟳]   │
│ ▾ Guardian  7 │ ┌ A Fazer 4 ─┐ ┌ Fazendo 2 ─┐ ┌ Bloqueado 1 ┐ ┌ Feito 3 ────┐  │
│   • guardian_ │ │ [card]     │ │ [card]     │ │ [card]      │ │ [card]      │  │
│     whatsapp 3│ │ [card]     │ │            │ │ motivo…     │ │ (14 dias)   │  │
│   • mercado-  │ │ + adicionar│ │            │ │             │ │ ver todas   │  │
│     pago     2│ └────────────┘ └────────────┘ └─────────────┘ └─────────────┘  │
│   • Sem projeto│                                                                   │
│ ▸ Tela Web  5 │                                                                    │
│ [+ Squad]     │                                                                    │
└───────────────┴────────────────────────────────────────────────────────────────────┘
```
- **Painel esquerdo (árvore):** squads com os projetos dentro. Cada nó mostra a contagem de tarefas **abertas** (tudo que não é `FEITO`). Cada squad tem também o nó "Sem projeto".
  - Selecionar um **squad** mostra todas as tarefas dele; selecionar um **projeto** filtra por ele.
  - Clique direito no squad: Novo projeto, Editar, Arquivar, Excluir. No projeto: Editar, Arquivar, Excluir, Copiar caminho do `contextoMd`.
  - Botão "+ Squad" no rodapé. Um switch "Mostrar arquivados" fica oculto por padrão.
- **Board:** 4 colunas de largura igual, com rolagem vertical própria e cabeçalho com nome e contagem. A coluna "Bloqueado" tem destaque em vermelho suave.
  - A coluna **Feito** mostra só as concluídas nos **últimos 14 dias**. O link "ver todas" carrega o resto.
  - Na coluna A Fazer há um campo "+ Adicionar tarefa": digitar o título e dar Enter cria a tarefa no squad e projeto selecionados, com prioridade média.

### 5.2 Card
- **Título**, com no máximo 3 linhas.
- **Chip do projeto**, só quando a visão é do squad inteiro.
- **Prioridade:** faixa colorida na borda esquerda (ALTA `-color-danger-emphasis`, MÉDIA `-color-warning-emphasis`, BAIXA `-color-fg-subtle`) e ícone com dica.
- **Tags** em chips pequenos.
- **Ícone de link**, quando houver. O clique abre a URL no navegador, ou o `.md` no editor padrão (caminho `docs/` resolvido a partir da raiz `CLAUDE/`, configurável).
- **Em `BLOQUEADO`:** o motivo aparece em texto vermelho abaixo do título.
- **Em `FEITO`:** título riscado e mais claro, com a data de conclusão.
- **Interações:**
  - Duplo-clique ou Enter abre a edição.
  - Clique direito abre o menu: Mover para (submenu com as 4 colunas), Abrir link, Duplicar, Excluir.
  - **Arrastar e soltar** entre colunas e dentro da mesma coluna (reordenar). Na hora de soltar aparece um indicador de posição, e a ação chama `POST /mover` com `antesDeId`. Se a API falhar, o card volta para onde estava e aparece um toast de erro.
  - Soltar em **Bloqueado** abre um diálogo pedindo o motivo (obrigatório). Cancelar desfaz o movimento.

### 5.3 Formulário da tarefa (Nova, Editar, Duplicar)
Campos:
- Título\*
- Squad\* (combo)
- Projeto (combo filtrado pelo squad, com a opção "Sem projeto")
- Status (seletor segmentado com as 4 colunas)
- Motivo do bloqueio (visível e obrigatório só em Bloqueado)
- Prioridade (Alta / Média / Baixa)
- Tags: campo com chips. Enter ou vírgula adiciona; o autocomplete sugere as tags já usadas.
- Link: validado como URL ou caminho `.md`. Tem um botão "Abrir" ao lado.
- Descrição (área de texto)

Mostrar também "Criada em", "Atualizada em" e "Concluída em" (só leitura). A validação aparece no próprio campo. Se a API devolver 400 ou 409, a mensagem aparece no topo do formulário sem fechá-lo, como no formulário de lembretes.

### 5.4 Squad e projeto
Diálogos pequenos:
- **Squad:** slug, nome e ordem.
- **Projeto:** squad, slug, nome, descrição e `contextoMd`. Sugira o `contextoMd` automaticamente como `<squad>/projects/<slug>/README.md`.

O slug é gerado a partir do nome (minúsculas, sem acentos, espaços viram `-`), mas pode ser editado.

### 5.5 Barra e atalhos
- **Busca:** local, sem diferenciar maiúsculas nem acentos, em título, descrição, tags, motivo e projeto.
- **Filtros:** Prioridade (multi) e Tag (multi). Um chip "Limpar filtros" aparece quando há algum ativo.
- **Atalhos:** `Ctrl+N` nova tarefa, `F5` atualizar, `Ctrl+F` buscar, `Delete` excluir o card selecionado. `Ctrl+1` a `Ctrl+4` move o card selecionado para a coluna correspondente.
- **Lembrar entre execuções** (`java.util.prefs`): o último squad ou projeto selecionado e a largura do painel esquerdo.
- **Estados vazios:**
  - API offline: o mesmo padrão da tela de lembretes;
  - nenhum squad: "Crie o primeiro squad";
  - coluna vazia: texto discreto "Nada aqui".

### 5.6 Código
```
dev.interfacegrafica.tarefas/
  TarefasTela, TarefasViewModel
  BoardView, ColunaView, CardTarefa      # componentes do kanban
  TarefaFormDialog, SquadDialog, ProjetoDialog
  TarefaFormValidator, Slug, FiltroTarefas   # classes puras, testadas
client/  TarefasClient + dto/ (SquadDto, ProjetoDto, TarefaRequest/Response, MoverRequest)
```
O `TarefasClient` segue o padrão do `LembretesClient` (sem JavaFX; `ApiException`/`ApiOfflineException`).

## 6. Testes
**API (módulo `tarefas`):**
- Domínio puro: regras de status (`motivoBloqueio` obrigatório; `concluidaEm` entra e sai), validação de tags e link, slug.
- Cálculo de `ordem` no `mover`: no fim, antes de outra, na coluna vazia e reordenando na mesma coluna.
- Repositórios JDBC num SQLite temporário: filtros do GET (squad, projeto, `0` = sem projeto, status repetido, tag, busca sem acento, `concluidasDesde`).
- Web (`@WebMvcTest` ou `@SpringBootTest`):
  - 201, 400 (projeto de outro squad, arquivado, Bloqueado sem motivo), 404 e 409 (excluir squad/projeto com tarefas, slug duplicado);
  - campo desconhecido no corpo → 400.
- `ApiApplicationTest` passa a conferir também as tabelas `squad`, `projeto` e `tarefa`.

**Desktop:**
- `TarefasClientTest` com servidor HTTP falso: rotas, corpo do `mover` e erros.
- `TarefaFormValidatorTest`, `SlugTest` e `FiltroTarefasTest` (busca, prioridade e tags combinadas).

## 7. Documentação
- `app/api/README.md`:
  - incluir o módulo `tarefas` na árvore;
  - adicionar a seção "Módulo tarefas", com as rotas, um exemplo de corpo e as regras de status;
  - atualizar o `Dockerfile` (COPY do módulo).
- `app/interface-grafica/README.md`: incluir a tela Tarefas, com o que ela faz e os atalhos.
- `docs/INDEX.md`: atualizar a linha da `api`, incluindo o módulo `tarefas`.

## 8. Integração com o Claude (substitui o Quadro A Fazer)
Depois que a API estiver no ar:
1. **Carga inicial** (feita pelo Claude, via API, não por SQL fixo no código):
   - um squad para cada time em `init.json` → `times` (`guardian`, `tela-web`, `pld`, `shared`);
   - um projeto para cada pasta em `docs/<squad>/projects/`, com o `contextoMd` apontando para o README dela.
2. **`CLAUDE.md`:** trocar a seção "Quadro A Fazer" por "Board de tarefas", com as regras:
   - **Criar tarefa sempre que identificar algo que o Messias precisa fazer depois** (pendência de ata ou daily, pedido de terceiros, "preciso…", "depois eu…"), sem esperar pedido, e **avisar na resposta** com o título curto, o squad e o projeto.
   - **Antes de criar, buscar** (`GET /api/tarefas?squadId=…&busca=…`) para não duplicar.
   - **Squad e projeto** vêm do assunto (a pasta em `docs/`). Se o projeto ainda não existir no board, criar o projeto junto.
   - **Link** = a ata ou daily de origem (caminho relativo a `docs/`). **Tag** `daily` ou `reuniao` conforme a origem.
   - **Mover para `FEITO`** quando o Messias disser que fez, e avisar. **Mover para `BLOQUEADO`, com o motivo,** quando ele disser que está travado esperando alguém.
   - Nunca colocar segredos em título, descrição ou link.
3. **`init.json` e `init.example.json`:** remover `quadro_a_fazer_url`.

## 9. Fora de escopo
- Responsável, data limite e estimativa (dá para adicionar depois como campos novos).
- Sprints/iterações, swimlanes, limite de WIP e histórico de alterações.
- Integração com o Azure DevOps real, notificações no Discord sobre tarefas e anexos.
- Vincular tarefas a lembretes (módulos não dependem um do outro; se precisar, isso entra depois via `shared` ou eventos).

## 10. Critérios de aceite
1. `docker compose up -d --build` sobe a `api` com os módulos `lembretes` e `tarefas`; os lembretes existentes continuam iguais.
2. Crio um squad, um projeto e uma tarefa pela tela, e eles aparecem também em `GET /api/tarefas`.
3. Arrasto um card de A Fazer para Fazendo e reordeno dentro da coluna. Depois de F5, a ordem continua a mesma.
4. Soltar em Bloqueado pede o motivo; cancelar desfaz o movimento. O motivo aparece no card.
5. Mover para Feito risca o card e preenche `concluidaEm`. Depois de 14 dias, ele só aparece em "ver todas".
6. Excluir um squad com tarefas mostra o erro 409 de forma amigável. Arquivar funciona.
7. Os filtros de busca, prioridade e tag se combinam corretamente. Selecionar um projeto na árvore filtra o board.
8. Com a API parada, a tela abre normalmente e mostra o estado offline. Ao voltar, recarrega sozinha.
9. O Claude consegue, só via HTTP, criar uma tarefa a partir de uma daily e movê-la para Feito.
10. Todos os testes passam (API via Docker e desktop via `mvnw`).
