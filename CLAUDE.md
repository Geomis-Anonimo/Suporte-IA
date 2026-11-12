# CLAUDE.md — Base de conhecimento de projetos

Base de conhecimento pessoal: guarda o **contexto de cada projeto** (reuniões, decisões, pendências, debug) para consulta futura e uso como base RAG. As **notas ficam em `docs/`** (não versionadas); o código/serviços ficam fora dela (ex.: `lembretes-api/`).

## Primeiro passo: dados do usuário (`init.json`)
Os dados pessoais **não ficam neste arquivo**. Em toda conversa, leia o [`init.json`](init.json) da raiz (ignorado pelo git) e use:
- `nome` / `email` / `idioma`: quem é o usuário. Onde este arquivo diz "o usuário", é essa pessoa; trate-a pelo `nome`.
- `quadro_a_fazer_url`: artifact do quadro "A Fazer" (ver seção abaixo).
- `times.<time>.meet_fixo`: link fixo e permanente do Meet de cada time (dailies/recorrentes).
- `glossario`: siglas e nomes internos, inclusive como as transcrições costumam errá-los.

**Se `init.json` não existir** (repositório recém-clonado): copie `init.example.json` para `init.json`, peça ao usuário para preencher os campos (ou preencha com as respostas dele) e só então continue. Nunca commitar `init.json`, `.env` nem `docs/`.

## Estrutura
```
CLAUDE/
├── CLAUDE.md                      # este arquivo (genérico, versionado)
├── init.json / init.example.json  # dados do usuário (o real é local); o exemplo é o modelo
├── docker-compose.yml             # serviços (ex.: lembretes-api); .env.example -> .env
├── lembretes-api/                 # código (Spring Boot + SQLite), ver README dele
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
- **Dailies:** quando o usuário enviar a transcrição de uma daily, criar `docs/<time>/dailies/AAAA-MM-DD-daily.md` (participantes, resumo, impedimentos por pessoa, assuntos, pendências), atualizar o índice em `docs/<time>/dailies/README.md`, criar cards das pendências do usuário e, se o assunto pertencer a um projeto, linkar/atualizar o README dele.
- Transcrições automáticas são ruidosas: marcar inferências incertas com **(?)** e não inventar detalhes.
- **Nunca gravar segredos** (tokens, secrets, senhas, valores de .env, URLs de webhook) em notas, cards ou arquivos versionados. Registrar só o nome da variável e o que ela faz.
- Seções curtas e autocontidas, pensadas para busca semântica (títulos descritivos, termos exatos como nomes de serviços, rotas, variáveis e códigos de erro).

## Glossário
Está em `init.json` → `glossario`. Ao ver um termo desconhecido numa transcrição, consulte-o antes de supor; se descobrir um termo novo, proponha acrescentá-lo lá.

## Quadro "A Fazer" (manter atualizado)
Os afazeres do usuário ficam num quadro simples (artifact) cujo link está em `init.json` → `quadro_a_fazer_url`, com colunas **A FAZER** e **FEITO**. Os cartões ficam na coleção `cards` do banco do artifact (acesso via ferramenta `ArtifactData`, com essa `url`). Se `quadro_a_fazer_url` estiver vazio, ofereça criar o quadro e grave a URL no `init.json`.

Regras para o Claude, em toda conversa:
- **Criar o card sempre que identificar algo que o usuário precisa fazer depois** (pendência de reunião, pedido de terceiros, algo dito por ele como "preciso...", "depois eu..."). Não esperar pedido. **Avisar na resposta** que o card foi criado (título curto).
- **Mover para FEITO** quando o usuário disser que fez, ou quando ele arrastar no quadro. Avisar também.
- Antes de criar, **listar a coleção** e evitar duplicar card existente. Ao ver cards já em FEITO que foram reabertos, respeitar o estado atual.
- Formato do documento (`doc_id` curto e único, ex.: `mp-009`): `{title, note?, project?, status: "todo"|"done", createdAt: <ms>, doneAt?: <ms>}`. Título curto e acionável; detalhes em `note`; `project` = nome da pasta do projeto.
- Ao concluir: `update` com `status: "done"` e `doneAt: <ms>`. Ao reabrir: `status: "todo"` e remover `doneAt` (`{"__delete__": true}`). Sempre usar `if_version` lido na hora.
- Nunca colocar segredos nos cards. Pendências das atas continuam registradas também na ata; o card é o lembrete.

## Reuniões confirmadas (agenda)
Sempre que o usuário confirmar uma reunião (data e horário): criar o evento no **Google Agenda** com link do **Meet**, e devolver a ele o link e o e-mail/convite. Se a sessão não tiver ferramenta de Google Agenda conectada, **dizer isso** e entregar o link de criação pré-preenchido (calendar.google.com/calendar/render?action=TEMPLATE...) e o texto do convite; nunca afirmar que agendou sem confirmação da ferramenta. Registrar também o card no quadro A Fazer.

**Dailies / reuniões recorrentes do time: usar o link FIXO, nunca gerar um Meet novo.** O link permanente de cada time está em `init.json` → `times.<time>.meet_fixo` (e repetido na linha "Link (fixo, permanente)" de `docs/<time>/dailies/README.md`). Ao criar/atualizar o evento no Google Agenda: passar `googleMeetUrl` = esse link (e **não** usar `addGoogleMeetUrl`, que cria um link aleatório); colocar o mesmo link também na descrição do convite. Depois de criar, conferir (`get_event`) se o link do evento é o fixo; se vier diferente, corrigir com `update_event` (`googleMeetUrl`). Se o evento já foi enviado com link aleatório, atualizar com o link fixo e avisar o usuário.

## Contatos (e-mails)
E-mails dos times ficam em `docs/contatos.json`, agrupados por time. Ao agendar reunião/convidar, usar esse arquivo. **Se algum participante novo (ou sem e-mail no arquivo) entrar numa reunião, perguntar ao usuário se ele tem o e-mail e avisá-lo** de que falta; ao receber, atualizar o JSON.

## Lembretes (lembretes-api)
Para "me lembra toda quinta..." ou "marquei reunião sexta 15h", use a API local descrita em [lembretes-api/README.md](lembretes-api/README.md): ela avisa no Discord com participantes e o `.md` de contexto. Os caminhos de `contextoMd` são relativos a `docs/`.

## Projetos
O índice dos projetos está em `docs/INDEX.md` (local). Abra-o para localizar o README de cada projeto.
