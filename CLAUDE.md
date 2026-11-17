# CLAUDE.md — Base de conhecimento de projetos

Base de conhecimento pessoal: guarda o **contexto de cada projeto** (reuniões, decisões, pendências, debug) para consulta futura e uso como base RAG. As **notas ficam em `docs/`** (não versionadas); o código/serviços ficam fora dela, em `app/`.

## Primeiro passo: dados do usuário (`init.json`)
Os dados pessoais **não ficam neste arquivo**. Em toda conversa, leia o [`init.json`](init.json) da raiz (ignorado pelo git) e use:
- `nome` / `email` / `idioma`: quem é o usuário. Onde este arquivo diz "o usuário", é essa pessoa; trate-a pelo `nome`.
- `times.<time>.meet_fixo`: link fixo e permanente do Meet de cada time (dailies/recorrentes).
- `glossario`: siglas e nomes internos, inclusive como as transcrições costumam errá-los.

**Se `init.json` não existir** (repositório recém-clonado): copie `init.example.json` para `init.json`, peça ao usuário para preencher os campos (ou preencha com as respostas dele) e só então continue. Nunca commitar `init.json`, `.env` nem `docs/`.

## Estrutura
```
CLAUDE/
├── CLAUDE.md                      # este arquivo (genérico, versionado)
├── init.json / init.example.json  # dados do usuário (o real é local); o exemplo é o modelo
├── docker-compose.yml             # serviços (ex.: api); .env.example -> .env
├── app/                           # todo o código
│   ├── api/                       # API Spring Boot + SQLite, multi-módulo (shared, lembretes, tarefas), ver README
│   └── interface-grafica/         # app desktop JavaFX (uma tela por funcionalidade: Lembretes, Tarefas), ver README
├── prompts/                       # prompts/especificações aprovados antes de desenvolver
└── docs/                          # TODAS as notas de projeto (local, não versionado)
    ├── INDEX.md                   # índice dos projetos (mantido pelo Claude)
    ├── contatos.json              # e-mails dos times
    └── <time>/                    # nome em minúsculas, hífens (ex.: time-a, tela-web)
        ├── dailies/               # uma nota por daily: AAAA-MM-DD-daily.md + README (índice)
        ├── alinhamento/           # (opcional) inventário de sistemas + atas de alinhamento com a gerência
        └── projects/
            └── <nome-do-projeto>/
                ├── README.md          # visão geral, índice e estado atual
                ├── reunioes/
                │   └── AAAA-MM-DD-<assunto>.md
                ├── anexos/            # HTML/PDF originais recebidos
                └── fluxo-<assunto>.md # fluxos documentados (opcional)
```

## Convenções
- Idioma: o de `init.json` (padrão português BR). Nomes de arquivo em minúsculas, sem acentos, com hífens.
- Cada reunião: data/participantes, contexto, decisões, testes, pendências (checklist), dicas de debug.
- Ao adicionar uma reunião, **atualizar o índice e o "Estado atual"** do README do projeto e o `docs/INDEX.md`.
- **Dailies:** quando o usuário enviar a transcrição de uma daily, criar `docs/<time>/dailies/AAAA-MM-DD-daily.md` (participantes, resumo, impedimentos por pessoa, assuntos, pendências), atualizar o índice em `docs/<time>/dailies/README.md`, criar tarefas no board para as pendências do usuário (ver "Board de tarefas") e, se o assunto pertencer a um projeto, linkar/atualizar o README dele.
- Transcrições automáticas são ruidosas: marcar inferências incertas com **(?)** e não inventar detalhes.
- **Nunca gravar segredos** (tokens, secrets, senhas, valores de .env, URLs de webhook) em notas, tarefas ou arquivos versionados. Registrar só o nome da variável e o que ela faz.
- Seções curtas e autocontidas, pensadas para busca semântica (títulos descritivos, termos exatos como nomes de serviços, rotas, variáveis e códigos de erro).

## Glossário
Está em `init.json` → `glossario`. Ao ver um termo desconhecido numa transcrição, consulte-o antes de supor; se descobrir um termo novo, proponha acrescentá-lo lá.

## Board de tarefas (módulo `tarefas` da `api`; manter atualizado)
Os afazeres do usuário ficam no **board de tarefas** da API local (`http://127.0.0.1:8080`), no estilo Azure Boards: **Squad** (time; slug = pasta `docs/<time>/`) → **Projeto** (slug = pasta `docs/<time>/projects/<projeto>/`; opcional) → **Tarefa**. As colunas são `A_FAZER`, `FAZENDO`, `BLOQUEADO` (exige `motivoBloqueio`) e `FEITO`. O usuário vê e arrasta os cards na tela "Tarefas" da `app/interface-grafica`. Rotas e exemplos estão em [app/api/README.md](app/api/README.md), na seção "Módulo tarefas".

Regras para o Claude, em toda conversa:
- **Criar uma tarefa sempre que identificar algo que o usuário precisa fazer depois** (pendência de reunião ou daily, pedido de terceiros, algo dito por ele como "preciso...", "depois eu..."). Não esperar pedido. **Avisar na resposta** com o título curto e o squad/projeto.
- **Antes de criar, buscar** para não duplicar: `GET /api/tarefas?squadId=<id>&busca=<palavras>`. Se existir, atualizar em vez de criar. Respeitar o estado atual: se o usuário reabriu ou moveu um card, não desfazer.
- **Squad e projeto** saem do assunto (a pasta em `docs/`). IDs: `GET /api/squads` e `GET /api/projetos?squadId=<id>`. Se o projeto existe em `docs/` mas não no board, criar antes (`POST /api/projetos` com `slug` = nome da pasta e `contextoMd` = `<time>/projects/<slug>/README.md`). Se a pendência não é de projeto nenhum, criar sem `projetoId` (aparece em "Sem projeto"). Se o time ainda não existe como squad, criar o squad (`slug` = nome da pasta do time).
- **Campos:**
  - `titulo` curto e acionável;
  - detalhes em `descricao`;
  - `link` = a ata ou daily de origem, como caminho relativo a `docs/` (ou a URL do PR/card);
  - `tags`: `daily` ou `reuniao` conforme a origem, mais outras úteis (`gmud`, `bug`…);
  - `prioridade` `ALTA` só quando houver urgência explícita.
- **Mover** com `POST /api/tarefas/{id}/mover` `{"status": ...}`:
  - para `FEITO` quando o usuário disser que fez;
  - para `BLOQUEADO`, com `motivoBloqueio`, quando ele disser que está esperando alguém;
  - para `FAZENDO` quando ele começar.

  Avisar o usuário em todos os casos.
- **Acentos (Windows/Git Bash):** mandar o corpo JSON de um **arquivo UTF-8** (`curl --data-binary @arquivo.json -H "Content-Type: application/json; charset=utf-8"`), nunca com `-d '...'` inline, porque o terminal converte os acentos e a API responde 400.
- Nunca colocar segredos em título, descrição ou link. Pendências das atas continuam registradas também na ata; a tarefa é o lembrete.
- Se a API não responder, avisar e sugerir `docker compose up -d` na raiz (ou o botão "Iniciar API" do executável `Interface Grafica.lnk`).

## Reuniões confirmadas (agenda)
Sempre que o usuário confirmar uma reunião (data e horário): criar o evento no **Google Agenda** com link do **Meet**, e devolver a ele o link e o e-mail/convite. Se a sessão não tiver ferramenta de Google Agenda conectada, **dizer isso** e entregar o link de criação pré-preenchido (calendar.google.com/calendar/render?action=TEMPLATE...) e o texto do convite; nunca afirmar que agendou sem confirmação da ferramenta. Registrar também a tarefa no board (ver "Board de tarefas").

**Dailies / reuniões recorrentes do time: usar o link FIXO, nunca gerar um Meet novo.** O link permanente de cada time está em `init.json` → `times.<time>.meet_fixo` (e repetido na linha "Link (fixo, permanente)" de `docs/<time>/dailies/README.md`). Ao criar/atualizar o evento no Google Agenda: passar `googleMeetUrl` = esse link (e **não** usar `addGoogleMeetUrl`, que cria um link aleatório); colocar o mesmo link também na descrição do convite. Depois de criar, conferir (`get_event`) se o link do evento é o fixo; se vier diferente, corrigir com `update_event` (`googleMeetUrl`). Se o evento já foi enviado com link aleatório, atualizar com o link fixo e avisar o usuário.

## Contatos (e-mails)
E-mails dos times ficam em `docs/contatos.json`, agrupados por time. Ao agendar reunião/convidar, usar esse arquivo. **Se algum participante novo (ou sem e-mail no arquivo) entrar numa reunião, perguntar ao usuário se ele tem o e-mail e avisá-lo** de que falta; ao receber, atualizar o JSON.

## Lembretes (módulo `lembretes` da `api`)
Para "me lembra toda quinta..." ou "marquei reunião sexta 15h", use a API local descrita em [app/api/README.md](app/api/README.md) (rotas `/api/lembretes`): ela avisa no Discord. **A API não usa IA nem chave de API: o texto do aviso é escrito por você (Claude), ao cadastrar.**

Regras para o Claude:
- **Ao cadastrar:** antes do `POST /api/lembretes`, reúna o contexto do assunto em `docs/` (README do projeto, atas, dailies) e na sessão atual; escreva o campo `mensagem` em linguagem natural e humana (o que será abordado, pendências, quem participa, o que o usuário ficou de ver), preencha `participantes` e `contextoMd` (caminho do `.md` principal, relativo a `docs/`). Confirme ao usuário o horário do aviso (`proximoDisparo`).
- **Manter atualizado:** sempre que entrar informação nova sobre um assunto (nova daily/ata, mudança de escopo, algo que o usuário ficou de ver), liste os lembretes ativos (`GET /api/lembretes?ativo=true`), localize os ligados ao assunto/projeto (mesmo `contextoMd` ou título) e substitua por uma versão atualizada com `PUT /api/lembretes/{id}` (equivale a apagar e salvar de novo, mantendo o id). Avise o usuário do que mudou. Se o assunto foi resolvido/encerrado, ofereça `DELETE`.
- Se a API não responder em `http://127.0.0.1:8080`, avise e sugira `docker compose up -d` na raiz. Nunca grave a URL do webhook em notas.

## Projetos
O índice dos projetos está em `docs/INDEX.md` (local). Abra-o para localizar o README de cada projeto.
