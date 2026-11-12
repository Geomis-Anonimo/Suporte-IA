# lembretes-api

API de lembretes: você pede ao Claude para anotar uma reunião/rotina, ela registra e **avisa no Discord** no horário combinado, com participantes e o `.md` de contexto. Spring Boot 3 + SQLite, arquitetura hexagonal, roda em Docker (não precisa de Java instalado).

Decisões do refinamento: [REFINAMENTO.md](REFINAMENTO.md).

## Como subir
O `docker-compose.yml` fica na **raiz `CLAUDE/`** (compartilhado: novos serviços entram como novos `services:` nele).
1. Na raiz `CLAUDE/`, copie `.env.example` para `.env` e preencha `DISCORD_WEBHOOK_URL` (e, opcionalmente, `ANTHROPIC_API_KEY`).
2. Na raiz `CLAUDE/`: `docker compose up -d --build`
3. API em `http://127.0.0.1:8080` (só acessível na própria máquina). Logs: `docker logs -f lembretes-api`.

- **Banco:** SQLite em `lembretes-api/data/lembretes.db` (volume `./data`). Para backup, copie o arquivo.
- **Contexto:** a pasta `docs/` é montada como somente leitura em `/contexto`; `contextoMd` é o caminho relativo a ela (ex.: `time-a/projects/projeto-x/README.md`). Só lê `.md` dentro dessa raiz.
- **Só funciona com o Docker/PC ligados.** `restart: unless-stopped` religa o container com o Docker.
- **Testes (sem Java local):** `docker run --rm -v "${PWD}:/app" -v lembretes-m2:/root/.m2 -w /app maven:3.9.9-eclipse-temurin-17 mvn -B test`

## Como o aviso funciona
- Job a cada 60 s procura lembretes cujo disparo (`ocorrência − antecedência`) está a até **1 min** de acontecer. Com antecedência padrão de 5 min, o aviso sai quando faltam 5–6 min ("Daqui 5 minutos…").
- Texto: se `ANTHROPIC_API_KEY` estiver definida, a IA (modelo `claude-haiku-5-5`, custo muito baixo) escreve o aviso em linguagem natural usando título, participantes e o trecho do `.md`. Sem chave ou em falha, usa um texto padrão.
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
  "recorrencia": { "tipo": "UNICA", "dataHora": "2026-11-13T15:00:00" },
  "antecedenciaMin": 5,
  "contextoMd": "time-a/projects/projeto-x/README.md",
  "participantes": [ { "nome": "Mario", "detalhe": "empresa X" }, { "nome": "Carla", "detalhe": "gerente" } ]
}
```
```json
{ "titulo": "Reunião X", "recorrencia": { "tipo": "SEMANAL", "diaSemana": "THURSDAY", "hora": "09:00" } }
```
Erros: `400` (validação/data passada), `404`, `502` (Discord indisponível em `/testar`); corpo `{"erro": "..."}`.

## Arquitetura (hexagonal)
```
src/main/java/dev/lembretes/lembrete/
  core/                    # sem Spring Web/JDBC/HTTP; sem anotações do Spring
    domain/                # Lembrete, Recorrencia (sealed), Participante, StatusDisparo
    application/           # casos de uso + MontadorAviso
      port/                # LembreteRepository, DisparoRepository, NotificadorPort,
                           # LeitorContextoPort, RedatorMensagemPort, RelogioPort
  infra/
    persistence/           # JdbcLembreteRepository, JdbcDisparoRepository (SQLite)
    jobs/                  # LembreteScheduler (@Scheduled) -> DispararLembretesVencidosService
    discord/               # DiscordWebhookNotificador
    filesystem/            # FileSystemLeitorContexto
    claude/                # ClaudeRedatorMensagem (API da Anthropic)
    config/                # LembretesConfig: liga portas aos adaptadores
  presentation/
    controller/            # LembreteController, ApiExceptionHandler
    dto/                   # LembreteRequest/Response, RecorrenciaDto, ParticipanteDto
```
Trocar JDBC por JPA = nova implementação de `LembreteRepository`; o `core` não muda.

## Segredos
`DISCORD_WEBHOOK_URL` e `ANTHROPIC_API_KEY` vêm só do `.env` da raiz `CLAUDE/` (no `.gitignore`). Nunca gravar a URL do webhook em arquivos versionados ou em notas.

## Como pedir ao Claude
"Anota reunião sexta 15h sobre Projeto X, com Mario e Carla" → o Claude faz o `POST`, preenchendo `contextoMd` com o README/ata do projeto, e confirma. "Toda quinta às 9h me lembra da reunião X" → `SEMANAL`.

## Fora de escopo (por ora)
WhatsApp (envio pago/complexo), autenticação, UI, integração com Google Agenda.
