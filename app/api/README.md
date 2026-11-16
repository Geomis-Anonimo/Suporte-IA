# api

API local da base CLAUDE, organizada como **monolito modular**: cada domínio é um módulo Maven independente, e todos rodam juntos num único Spring Boot, num único container e num único SQLite. Módulos de domínio: **lembretes** e **tarefas** (board estilo Azure, [seção abaixo](#módulo-tarefas)). No módulo **lembretes**, você pede ao Claude para anotar uma reunião ou rotina, a API registra e **avisa no Discord** no horário combinado, com um texto em linguagem natural, os participantes e o `.md` de contexto. Spring Boot 3, SQLite, arquitetura hexagonal em cada módulo, roda em Docker (não precisa de Java instalado).

Decisões do refinamento original (quando ainda era só `lembretes-api`): [REFINAMENTO.md](REFINAMENTO.md).

## Módulos
```
app/api/
  pom.xml          # pai (packaging pom): lista os módulos e as versões
  shared/          # api-shared: o que todo módulo usa
                   #   core/      DadosInvalidosException, RecursoNaoEncontradoException
                   #   core/port/ RelogioPort (agora + fuso), NotificadorPort (enviar texto)
                   #   infra/     DiscordWebhookNotificador, SharedConfig (beans de relógio e Discord)
                   #   presentation/ApiExceptionHandler  -> erros viram {"erro": "..."} (400/404/502)
  lembretes/       # api-lembretes: domínio de lembretes (rotas /api/lembretes)
                   #   resources/db/lembretes-schema.sql  -> tabelas lembrete, disparo
  tarefas/         # api-tarefas: board de tarefas (rotas /api/squads, /api/projetos, /api/tarefas)
                   #   resources/db/tarefas-schema.sql    -> tabelas squad, projeto, tarefa
  bootstrap/       # api-bootstrap: ApiApplication + application.properties; gera o api.jar
  data/api.db      # banco SQLite (volume do Docker, não versionado)
```
Regras dos módulos:
- **Um módulo de domínio depende só de `shared`, nunca de outro domínio.** O Maven garante isso: um módulo não enxerga as classes de outro sem declarar a dependência no `pom.xml`.
- **Cada módulo é dono das próprias tabelas.** O schema fica em `src/main/resources/db/<modulo>-schema.sql` (com `CREATE TABLE IF NOT EXISTS`) e é aplicado na subida pelo padrão `classpath*:db/*-schema.sql`.
- **Cada módulo tem o próprio `@Configuration`** em `infra/config/`. O `bootstrap` encontra todos pelo component scan a partir de `dev.api`.
- **Propriedades:** as comuns usam o prefixo `api.*` (`api.fuso`, `api.discord.webhook-url`) e as de cada módulo usam o nome dele (`lembretes.*`).

### Como criar um módulo novo (ex.: `tarefas`)
1. Crie a pasta `app/api/tarefas/` com um `pom.xml` (artifactId `api-tarefas`, pai `api-parent`, dependência de `api-shared`, como em `lembretes/pom.xml`).
2. Crie o código em `dev.api.tarefas.{core,infra,presentation}` e o schema em `src/main/resources/db/tarefas-schema.sql`.
3. Inclua `<module>tarefas</module>` e a versão de `api-tarefas` no `pom.xml` pai, e adicione a dependência no `bootstrap/pom.xml`.
4. No `Dockerfile`, inclua as linhas `COPY tarefas/pom.xml tarefas/` e `COPY tarefas/src tarefas/src`.

## Como subir
O `docker-compose.yml` fica na **raiz `CLAUDE/`** e é compartilhado: serviços novos entram nele como novos `services:`.
1. Na raiz `CLAUDE/`, copie `.env.example` para `.env` e preencha `DISCORD_WEBHOOK_URL`.
2. Na raiz `CLAUDE/`, rode `docker compose up -d --build`.
3. A API fica em `http://127.0.0.1:8080` (só acessível na própria máquina). Logs: `docker logs -f api`.

- **Banco:** SQLite em `app/api/data/api.db` (volume `/data`), com as tabelas de todos os módulos. Para backup, pare o container e copie o arquivo.
- **Sem IA e sem chave de API:** quem escreve o texto do aviso é o Claude da sessão (VS Code), ao cadastrar. A API só guarda e envia. O container também não lê arquivos: `contextoMd` é só uma referência exibida no aviso.
- **Só funciona com o Docker e o PC ligados.** Com `restart: unless-stopped`, o container volta sozinho quando o Docker sobe.
- **Testes (sem Java local),** rodados em `app/api/`: `docker run --rm -v "${PWD}:/app" -v lembretes-m2:/root/.m2 -w /app maven:3.9.9-eclipse-temurin-17 mvn -B test`. Inclui `ApiApplicationTest`, que sobe a aplicação inteira e confere se os schemas dos módulos foram aplicados.

## Módulo lembretes

### Como o aviso funciona
- Job a cada 60 s procura lembretes cujo disparo (`ocorrência − antecedência`) está a até **1 min** de acontecer. Com antecedência padrão de 5 min, o aviso sai quando faltam 5–6 min ("Daqui 5 min…").
- Texto do aviso: cabeçalho "⏰ Daqui N min: **título**" + `mensagem` (linguagem natural, escrita pelo Claude no cadastro) + participantes + caminho do `.md` de contexto. Sem `mensagem`, usa a `descricao`.
- Discord fora do ar: o lembrete continua pendente e tenta de novo no minuto seguinte, até 10 min após o horário; depois é descartado (`EXPIRADO` na tabela `disparo`).
- Única encerra após avisar; recorrentes calculam a próxima ocorrência. Fuso: `America/Sao_Paulo`.

### Rotas
| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/lembretes` | cria |
| GET | `/api/lembretes?ativo=true` | lista |
| GET | `/api/lembretes/{id}` | detalhe |
| PUT | `/api/lembretes/{id}` | substitui (recalcula próxima ocorrência) |
| DELETE | `/api/lembretes/{id}` | remove |
| POST | `/api/lembretes/{id}/testar` | envia o aviso agora, sem alterar o lembrete |

Corpo (POST/PUT). `antecedenciaMin` omitido = 5. Campos exigidos em `recorrencia`: `UNICA` → `dataHora`; `DIARIA` → `hora`; `SEMANAL` → `diaSemana` (`MONDAY`…`SUNDAY`) + `hora`; `MENSAL` → `diaMes` + `hora`.

```json
{
  "titulo": "Alinhamento Projeto X",
  "descricao": "Subida para produção",
  "mensagem": "Daqui a pouco é o alinhamento da subida do Projeto X com o Mario (empresa X) e a Carla (gerente). Ficou pendente validar o webhook em produção e confirmar a data de virada.",
  "recorrencia": { "tipo": "UNICA", "dataHora": "2026-11-13T15:00:00" },
  "antecedenciaMin": 5,
  "contextoMd": "time-a/projects/projeto-x/README.md",
  "participantes": [ { "nome": "Mario", "detalhe": "empresa X" }, { "nome": "Carla", "detalhe": "gerente" } ]
}
```
```json
{ "titulo": "Reunião X", "mensagem": "Rotina semanal de quinta: ...", "recorrencia": { "tipo": "SEMANAL", "diaSemana": "THURSDAY", "hora": "09:00" } }
```
Erros: `400` (validação/data passada), `404`, `502` (Discord indisponível em `/testar`); corpo `{"erro": "..."}`.

### Arquitetura do módulo (hexagonal)
```
lembretes/src/main/java/dev/api/lembretes/
  core/                    # sem Spring Web/JDBC/HTTP; sem anotações do Spring
    domain/                # Lembrete, Recorrencia (sealed), Participante, StatusDisparo
    application/           # casos de uso + MontadorAviso; LembreteNaoEncontradoException (-> 404 via shared)
      port/                # LembreteRepository, DisparoRepository (RelogioPort e NotificadorPort vêm do shared)
  infra/
    persistence/           # JdbcLembreteRepository, JdbcDisparoRepository (SQLite)
    jobs/                  # LembreteScheduler (@Scheduled) -> DispararLembretesVencidosService
    config/                # LembretesConfig: liga portas aos adaptadores
  presentation/
    controller/            # LembreteController
    dto/                   # LembreteRequest/Response, RecorrenciaDto, ParticipanteDto
```
Trocar JDBC por JPA é só escrever outra implementação de `LembreteRepository`: o `core` não muda.

## Módulo tarefas
Board de tarefas no estilo Azure Boards. Substitui o antigo "Quadro A Fazer" (artifact). Hierarquia:
```
Squad (time, ex.: guardian)              slug = pasta docs/<time>/
 └── Projeto (ex.: guardian_whatsapp)    slug = pasta docs/<time>/projects/<projeto>/  (opcional na tarefa)
      └── Tarefa                          colunas A_FAZER, FAZENDO, BLOQUEADO, FEITO
```
A tela "Tarefas" da [interface-grafica](../interface-grafica/README.md) mostra o board com arrastar e soltar. O Claude cria e move tarefas por HTTP, conforme as regras no `CLAUDE.md` da raiz.

### Regras
- **`BLOQUEADO` exige `motivoBloqueio`.** Ao sair de `BLOQUEADO`, o motivo é apagado.
- **`concluidaEm`** é preenchido ao entrar em `FEITO` e apagado ao sair.
- **Projeto:** se informado, tem que ser do mesmo squad da tarefa (senão 400).
- **Arquivados:** squad ou projeto arquivado não aceita tarefas novas (400) e some das listagens, a menos que se use `?incluirArquivados=true`.
- **Excluir:** squad ou projeto com filhos não pode ser excluído (409). Arquive ou mova as tarefas antes.
- **Slug:** minúsculas, números, `-` e `_`. É único (dentro do squad, no caso de projeto); repetido dá 409.
- **Tags:** viram minúsculas, sem repetidas, até 30 caracteres e sem espaços.
- **`link`:** URL `http(s)://` ou caminho `.md` relativo a `docs/`.
- **`ordem`** é calculada pela API: tarefa nova entra no fim da coluna, e o `/mover` renumera a coluna de destino.

### Rotas
| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/squads?incluirArquivados=false` | lista (ordem do menu), com `contagem` por status |
| POST / PUT | `/api/squads`, `/api/squads/{id}` | `{slug, nome, ordem?, arquivado?}` |
| DELETE | `/api/squads/{id}` | 204; 409 se tiver projetos/tarefas |
| GET | `/api/projetos?squadId=&incluirArquivados=false` | lista com `contagem` |
| POST / PUT | `/api/projetos`, `/api/projetos/{id}` | `{squadId, slug, nome, descricao?, contextoMd?, arquivado?}` |
| DELETE | `/api/projetos/{id}` | 204; 409 se tiver tarefas |
| GET | `/api/tarefas` | filtros: `squadId`, `projetoId` (`0` = sem projeto), `status` (repetível), `prioridade`, `tag`, `busca` (sem acento), `concluidasDesde` (ISO) |
| GET | `/api/tarefas/{id}` | detalhe (com `squadSlug`, `projetoSlug`, `projetoNome`) |
| POST | `/api/tarefas` | cria no fim da coluna |
| PUT | `/api/tarefas/{id}` | substitui os campos editáveis; se mudar a coluna, vai para o fim dela |
| POST | `/api/tarefas/{id}/mover` | `{status, antesDeId?, motivoBloqueio?}`: move para antes de `antesDeId` (ou para o fim) |
| DELETE | `/api/tarefas/{id}` | 204 |

Corpo da tarefa (POST/PUT). Campos calculados (`ordem` e datas) são recusados:
```json
{ "squadId": 1, "projetoId": 4, "titulo": "Validar template de cobrança na Omne",
  "descricao": "Combinado na daily: conferir variáveis do template antes da virada.",
  "status": "A_FAZER", "prioridade": "ALTA", "tags": ["daily", "omne"],
  "link": "guardian/dailies/2026-11-16-daily.md" }
```
**Acentos no Windows (Git Bash):** mande o corpo de um arquivo UTF-8, com `curl --data-binary @corpo.json -H "Content-Type: application/json; charset=utf-8"`. Com `-d '...'` inline, os acentos chegam corrompidos e a API responde 400 ("Corpo da requisicao invalido").

### Arquitetura do módulo
```
tarefas/src/main/java/dev/api/tarefas/
  core/domain/        Squad, Projeto, Tarefa (regras de status/tags/link), StatusTarefa, Prioridade, Slug, Textos
  core/application/   SquadService, ProjetoService, TarefaService (mover/ordem); Dados (comandos e visões)
    port/             SquadRepository, ProjetoRepository, TarefaRepository
  infra/persistence/  JdbcSquadRepository, JdbcProjetoRepository, JdbcTarefaRepository (tags em JSON)
  infra/config/       TarefasConfig
  presentation/       SquadController, ProjetoController, TarefaController + dto/
```
Testes: `TarefaTest` (domínio), `TarefaServiceTest` (serviços sobre um SQLite temporário) e `bootstrap/TarefasApiTest` (HTTP de ponta a ponta).

## Segredos
`DISCORD_WEBHOOK_URL` vem só do `.env` da raiz `CLAUDE/` (no `.gitignore`). Nunca gravar a URL do webhook em arquivos versionados ou em notas.

### Como pedir ao Claude (fluxo)
**Cadastrar.** Ex.: "anota reunião sexta 15h sobre Projeto X, com Mario e Carla" ou "toda quinta às 9h me lembra da reunião X".
1. O Claude reúne o contexto: README/atas/dailies do projeto em `docs/` e o que foi dito na sessão.
2. Escreve a `mensagem` em linguagem natural (o que será abordado, pendências, quem participa) e define `contextoMd` (o `.md` principal do assunto, relativo a `docs/`).
3. Faz o `POST /api/lembretes`, confere a resposta (`proximoDisparo`) e confirma para você.

**Manter atualizado.** Se aparecer novidade sobre o assunto (nova daily, ata, mudança de escopo ou algo que você ficou de ver), o Claude lista os lembretes ativos (`GET /api/lembretes?ativo=true`), acha os ligados àquele projeto (mesmo `contextoMd`/assunto) e **substitui** pelo texto atualizado com `PUT /api/lembretes/{id}` (equivale a apagar e salvar de novo, mas mantém o id), avisando o que mudou. Para tirar uma rotina: `DELETE`.

## Fora de escopo (por ora)
WhatsApp (envio pago/complexo), autenticação, integração com Google Agenda, redação por IA dentro da API.

A interface gráfica fica fora da API: é o app desktop [interface-grafica](../interface-grafica/README.md) (JavaFX), que consome estas rotas.
