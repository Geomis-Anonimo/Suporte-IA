# Lembretes API — Refinamento

Status: **refinado e implementado** em 2026-11-12. Documentação atual: [README.md](README.md) (este arquivo guarda o histórico das decisões).

## 0. Decisões tomadas
- Aviso só no **Discord** (webhook de canal); WhatsApp descartado (custo).
- Roda **só na máquina local**, em **Docker** (sem Java instalado). Maven dentro da imagem.
- **SQLite** em `lembretes-api/data/lembretes.db`.
- Antecedência padrão **5 min**; o job pega o que está a até 6 min e o texto é escrito em linguagem natural por IA (Claude), com texto padrão de fallback.
- Pasta `lembretes-api`, Maven. Webhook só por variável de ambiente (`.env`).
- Mudanças em relação ao rascunho abaixo: removido o campo `canais`; participantes são objetos `{nome, detalhe}`; pacote `dev.lembretes.lembrete`; Java 17.

---
Rascunho original (histórico):

## 1. Objetivo
API simples para eu pedir ao Claude "anota essa rotina/reunião" e ela **registrar** e **avisar no horário combinado** via Discord (e, se possível, no Claude).

Exemplos:
- **Rotina:** "Toda quinta, me lembra da reunião X" → aviso recorrente toda quinta.
- **Reunião pontual:** "Reunião sexta às 15h" → aviso (ex.: 30 min antes) no Discord com: título, horário, **qual .md dá contexto** (caminho + trecho) e **quem participa**.

## 2. Escopo (v1)
Dentro:
- CRUD de lembretes (criar, listar, consultar, editar, desativar/remover).
- Tipos: **única** (data/hora) e **recorrente** (diária, semanal por dia da semana, mensal por dia do mês).
- Antecedência configurável por lembrete (ex.: avisar 30 min antes).
- Campos de contexto: caminho de um `.md` e lista de participantes.
- Job que roda a cada minuto, acha lembretes vencidos e dispara notificação no Discord.
- Registro de disparos (evitar aviso duplicado e permitir auditoria).

Fora (por ora):
- Autenticação multiusuário, UI web, integração com Google Agenda, resumo do .md por IA.

## 3. Stack
- Java 21 + Spring Boot 3.x (Web, `@Scheduled`).
- SQLite via JDBC (`sqlite-jdbc`) com `JdbcClient`/`JdbcTemplate`; schema criado por `schema.sql` na subida.
- Discord via **webhook** (HTTP POST, sem bot).
- Testes: JUnit 5; casos de uso testados com fakes das portas (sem Spring).

## 4. Arquitetura hexagonal
Módulo único `lembrete`:

```
lembrete/
  core/                          # não conhece Spring Web, JDBC nem HTTP
    domain/
      Lembrete                   # record: id, titulo, descricao, recorrencia, antecedenciaMin,
                                 #   contextoMd, participantes, canais, proximoDisparo, ativo
      Recorrencia                # record/sealed: Unica(dataHora) | Diaria(hora) | Semanal(diaSemana, hora) | Mensal(dia, hora)
      Participante               # record: nome, (email opcional)
      Canal                      # enum: DISCORD (futuro: EMAIL...)
      Disparo                    # record: lembreteId, referencia(Instant), enviadoEm, status
    application/
      CriarLembreteService
      ListarLembretesService
      AtualizarLembreteService
      RemoverLembreteService
      DispararLembretesVencidosService   # caso de uso chamado pelo job
      port/
        LembreteRepository       # salvar, buscarPorId, listar, buscarVencidos(agora)
        DisparoRepository        # registrar / jaDisparado(lembreteId, referencia)
        NotificadorPort          # enviar(Notificacao)
        LeitorContextoPort       # lerTrecho(caminhoMd) -> Optional<String>
        RelogioPort              # agora()
  infra/
    persistence/                 # JdbcLembreteRepository, JdbcDisparoRepository (SQLite)
    jobs/                        # LembreteScheduler: @Scheduled(fixedRate=60s) -> DispararLembretesVencidosService
    discord/                     # DiscordWebhookNotificador implements NotificadorPort
    filesystem/                  # FileSystemLeitorContexto implements LeitorContextoPort
    config/                      # beans, Clock, propriedades
  presentation/
    controller/                  # LembreteController (REST)
    dto/                         # CriarLembreteRequest, LembreteResponse, ... (nunca expõem o domínio)
```

Regras:
- `core` só depende do JDK. Dependência aponta sempre para dentro (infra/presentation → core).
- Job só dispara o caso de uso; a regra "o que está vencido" vive no `core`.
- Trocar JDBC por JPA = nova implementação de `LembreteRepository`, sem tocar no `core`.

## 5. API REST (rascunho)
| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/lembretes` | cria lembrete |
| GET | `/api/lembretes` | lista (filtro `?ativo=true`) |
| GET | `/api/lembretes/{id}` | detalhe |
| PUT | `/api/lembretes/{id}` | edita |
| DELETE | `/api/lembretes/{id}` | remove/desativa |
| POST | `/api/lembretes/{id}/testar` | dispara agora (testar o Discord) |

Exemplo — reunião pontual:
```json
{
  "titulo": "Reunião de alinhamento Projeto X",
  "recorrencia": { "tipo": "UNICA", "dataHora": "2026-11-13T15:00:00-03:00" },
  "antecedenciaMin": 30,
  "contextoMd": "time-a/projects/projeto-x/README.md",
  "participantes": ["Fulano", "Ciclano"],
  "canais": ["DISCORD"]
}
```

Exemplo — rotina semanal:
```json
{
  "titulo": "Reunião X",
  "recorrencia": { "tipo": "SEMANAL", "diaSemana": "THURSDAY", "hora": "09:00" },
  "antecedenciaMin": 15,
  "canais": ["DISCORD"]
}
```

## 6. Modelo de dados (SQLite)
- `lembrete(id, titulo, descricao, recorrencia_tipo, recorrencia_json, antecedencia_min, contexto_md, participantes_json, canais, proximo_disparo, ativo, criado_em)`
- `disparo(id, lembrete_id, referencia, enviado_em, status, erro)` — `UNIQUE(lembrete_id, referencia)` garante idempotência.

Lógica: `proximo_disparo` = próxima ocorrência − antecedência. Após disparar, recorrentes recalculam a próxima; únicas ficam inativas.

## 7. Mensagem no Discord (rascunho)
```
🔔 Reunião de alinhamento Projeto X — sexta 13/11 às 15:00 (em 30 min)
👥 Participantes: Fulano, Ciclano
📄 Contexto: time-a/projects/projeto-x/README.md
> (trecho inicial / "Estado atual" do .md)
```

## 8. Como o Claude usa isso
Eu peço ao Claude em linguagem natural; ele traduz para um `POST /api/lembretes` (via curl/HTTP) e confirma o que foi registrado. Para reuniões, ele já preenche `contextoMd` com o README/ata do projeto e `participantes` a partir de `docs/contatos.json`.

## 9. Perguntas em aberto
1. **Aviso "por aqui" (no Claude):** a API não consegue empurrar mensagem para dentro do chat. Opções: (a) só Discord na v1; (b) Claude cria um agendamento próprio (cron/rotina) que avisa na sessão; (c) notificação push do Claude. Qual prefere?
2. **Discord:** webhook de canal é suficiente (recomendado) ou quer DM/bot com menção a você?
3. **Antecedência padrão:** 30 min? Aviso único ou também um "agora" na hora?
4. **Fuso:** fixo `America/Sao_Paulo`?
5. **Onde a API roda:** só na sua máquina (então só avisa com o PC ligado) ou num servidor/always-on?
6. **Caminho do .md:** relativo a `Documents/CLAUDE/docs` (raiz configurável por propriedade). Trecho enviado: início do arquivo ou seção "Estado atual"?
7. **Segurança:** só localhost sem autenticação na v1? A URL do webhook ficará em variável de ambiente (`DISCORD_WEBHOOK_URL`), nunca no repositório.
8. **Pasta/nome do projeto:** `lembretes-api` está bom?
9. **Build:** Maven ou Gradle?
10. **Participantes:** apenas nomes ou vincular a `docs/contatos.json` (e-mail)?
