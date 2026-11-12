# docs/ — suas notas de projeto

Esta pasta guarda o contexto dos seus projetos (reuniões, dailies, decisões, pendências). **O conteúdo dela não é versionado** (veja o `.gitignore` da raiz): só este README vai para o repositório.

Estrutura sugerida (o Claude cria conforme você enviar transcrições e atas):

```
docs/
├── INDEX.md               # índice dos seus projetos (o Claude mantém)
├── contatos.json          # e-mails por time: { "time-a": [ { "nome": "...", "email": "..." } ] }
└── <time>/
    ├── dailies/           # AAAA-MM-DD-daily.md + README (índice e link fixo do Meet)
    └── projects/<projeto>/
        ├── README.md      # visão geral e estado atual
        ├── reunioes/      # AAAA-MM-DD-<assunto>.md
        └── anexos/
```

As regras de como o Claude escreve e organiza tudo isso estão no `CLAUDE.md` da raiz.
