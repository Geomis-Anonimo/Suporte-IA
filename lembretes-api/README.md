# lembretes-api

API de lembretes: você pede ao Claude para anotar uma reunião/rotina, ela registra e **avisa no Discord** no horário combinado, com um texto em linguagem natural, os participantes e o `.md` de contexto. Spring Boot 3 + SQLite, arquitetura hexagonal, roda em Docker (não precisa de Java instalado).

Decisões do refinamento: [REFINAMENTO.md](REFINAMENTO.md).

## Como subir
O `docker-compose.yml` fica na **raiz `CLAUDE/`** (compartilhado: novos serviços entram como novos `services:` nele).
1. Na raiz `CLAUDE/`, copie `.env.example` para `.env` e preencha `DISCORD_WEBHOOK_URL`.
2. Na raiz `CLAUDE/`: `docker compose up -d --build`
3. API em `http://127.0.0.1:8080` (só acessível na própria máquina). Logs: `docker logs -f lembretes-api`.

- **Banco:** SQLite em `lembretes-api/data/lembretes.db` (volume `./data`). Para backup, copie o arquivo.
- **Sem IA e sem chave de API:** quem escreve o texto do aviso é o Claude da sessão (VS Code), ao cadastrar. A API só guarda e envia. O container também não lê arquivos: `contextoMd` é só uma referência exibida no aviso.
- **Só funciona com o Docker/PC ligados.** `restart: unless-stopped` religa o container com o Docker.
- **Testes (sem Java local):** `docker run --rm -v "${PWD}:/app" -v lembretes-m2:/root/.m2 -w /app maven:3.9.9-eclipse-temurin-17 mvn -B test`

## Como o aviso funciona
- Job a cada 60 s procura lembretes cujo disparo (`ocorrência − antecedência`) está a até **1 min** de acontecer. Com antecedência padrão de 5 min, o aviso sai quando faltam 5–6 min ("Daqui 5 min…").
- Texto do aviso: cabeçalho "⏰ Daqui N min: **título**" + `mensagem` (linguagem natural, escrita pelo Claude no cadastro) + participantes + caminho do `.md` de contexto. Sem `mensagem`, usa a `descricao`.
- Discord fora do ar: o lembrete continua pendente e tenta de novo no minuto seguinte, até 10 min após o horário; depois é descartado (`EXPIRADO` na tabela `disparo`).
- Única encerra após avisar; recorrentes calculam a próxima ocorrência. Fuso: `America/Sao_Paulo`.

## API
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

## Arquitetura (hexagonal)
```
src/main/java/dev/lembretes/lembrete/
  core/                    # sem Spring Web/JDBC/HTTP; sem anotações do Spring
    domain/                # Lembrete, Recorrencia (sealed), Participante, StatusDisparo
    application/           # casos de uso + MontadorAviso
      port/                # LembreteRepository, DisparoRepository, NotificadorPort, RelogioPort
  infra/
    persistence/           # JdbcLembreteRepository, JdbcDisparoRepository (SQLite)
    jobs/                  # LembreteScheduler (@Scheduled) -> DispararLembretesVencidosService
    discord/               # DiscordWebhookNotificador
    config/                # LembretesConfig: liga portas aos adaptadores
  presentation/
    controller/            # LembreteController, ApiExceptionHandler
    dto/                   # LembreteRequest/Response, RecorrenciaDto, ParticipanteDto
```
Trocar JDBC por JPA = nova implementação de `LembreteRepository`; o `core` não muda.

## Segredos
`DISCORD_WEBHOOK_URL` vem só do `.env` da raiz `CLAUDE/` (no `.gitignore`). Nunca gravar a URL do webhook em arquivos versionados ou em notas.

## Como pedir ao Claude (fluxo)
**Cadastrar.** Ex.: "anota reunião sexta 15h sobre Projeto X, com Mario e Carla" ou "toda quinta às 9h me lembra da reunião X".
1. O Claude reúne o contexto: README/atas/dailies do projeto em `docs/` e o que foi dito na sessão.
2. Escreve a `mensagem` em linguagem natural (o que será abordado, pendências, quem participa) e define `contextoMd` (o `.md` principal do assunto, relativo a `docs/`).
3. Faz o `POST /api/lembretes`, confere a resposta (`proximoDisparo`) e confirma para você.

**Manter atualizado.** Se aparecer novidade sobre o assunto (nova daily, ata, mudança de escopo ou algo que você ficou de ver), o Claude lista os lembretes ativos (`GET /api/lembretes?ativo=true`), acha os ligados àquele projeto (mesmo `contextoMd`/assunto) e **substitui** pelo texto atualizado com `PUT /api/lembretes/{id}` (equivale a apagar e salvar de novo, mas mantém o id), avisando o que mudou. Para tirar uma rotina: `DELETE`.

## Fora de escopo (por ora)
WhatsApp (envio pago/complexo), autenticação, UI, integração com Google Agenda, redação por IA dentro da API.
