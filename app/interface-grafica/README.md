# interface-grafica

Interface gráfica desktop (JavaFX) para as ferramentas desta base. Cada funcionalidade é uma tela no menu lateral e usa um módulo da [api](../api/README.md):

| Tela | Módulo da api | O que faz |
|---|---|---|
| **Lembretes** | `lembretes` | listar, buscar, criar, editar, duplicar, excluir e **testar no Discord** os lembretes |
| **Tarefas** | `tarefas` | board estilo Azure (squad → projeto → tarefa) com arrastar e soltar entre as colunas A Fazer / Fazendo / Bloqueado / Feito |

Especificações: [tela de lembretes](../../prompts/interface-grafica-tela-lembretes.md) e [board de tarefas](../../prompts/tarefas-board.md).

## Pré-requisitos
- **JDK 17 ou mais novo** (testado com o JDK 19). Não precisa de Maven instalado: o projeto usa o Maven Wrapper (`mvnw`).
- A **api rodando** em `http://127.0.0.1:8080`. Na raiz `CLAUDE/`: `docker compose up -d`.

## Executável (uso do dia a dia)
Para gerar ou atualizar o executável, rode na pasta `app/interface-grafica`:
```powershell
.\empacotar.ps1              # roda os testes antes; use -SemTestes para pular
# se o PowerShell bloquear scripts: powershell -ExecutionPolicy Bypass -File .\empacotar.ps1
```
Ele cria na raiz `CLAUDE/`:
- `Interface Grafica\Interface Grafica.exe`, com um Java mínimo embutido (cerca de 63 MB). **Não precisa de Java nem de Maven instalados para abrir.**
- `Interface Grafica.lnk`, um atalho para abrir com duplo clique. Dá para fixar na barra de tarefas ou no menu Iniciar.

Os dois ficam fora do git. Para gerar, é preciso um JDK 17+ (ele procura o `JAVA_HOME` ou `C:\Program Files\Java\jdk-*`). **Rode de novo depois de mudar o código**, com o app fechado.

O executável acha a pasta `CLAUDE` (e a `docs/`) subindo a partir de onde está. Se você mover a pasta `Interface Grafica` para outro lugar, defina a variável de ambiente `CLAUDE_DIR` com o caminho da pasta `CLAUDE`.

### Botão "Iniciar API"
Quando a API está fora do ar, a barra superior mostra o botão **Iniciar API**. Ele:
1. abre o Docker Desktop, se o Docker não estiver rodando, e espera até 3 minutos;
2. roda `docker compose up -d` na pasta `CLAUDE`;
3. espera até 90 s a API responder.

O botão mostra a etapa atual. Se algo falhar, aparece um toast com o motivo, por exemplo a saída do `docker compose`. A lógica fica em `sistema/IniciadorApi`.

## Como rodar (desenvolvimento)
```powershell
cd app/interface-grafica
$env:JAVA_HOME = "C:\Program Files\Java\jdk-19"   # se o java do PATH for o 8
.\mvnw.cmd javafx:run
```
Na primeira vez o wrapper baixa o Maven e as dependências (precisa de internet).

### Configuração
| O quê | Como (por ordem de prioridade) | Padrão |
|---|---|---|
| URL da api | `-Dapi.url=...`, env `API_URL` (os nomes antigos `lembretes.api.url`/`LEMBRETES_API_URL` continuam valendo) | `http://127.0.0.1:8080` |
| Pasta `CLAUDE` (onde está o `docker-compose.yml`, usada pelo botão "Iniciar API") | `-Dclaude.raiz=...`, env `CLAUDE_DIR` | procura subindo a partir da pasta atual e da pasta do app |
| Pasta `docs/` (para abrir os links `.md` das tarefas) | `-Dclaude.docs=...`, env `CLAUDE_DOCS_DIR` | `<CLAUDE>/docs`, ou procura subindo uma `docs/` que tenha `INDEX.md` |

## Como testar
```powershell
.\mvnw.cmd test
```
Os testes não usam a API real: os de cliente sobem um servidor HTTP falso (`com.sun.net.httpserver`).
- `LembretesClientTest` / `TarefasClientTest`: rotas, JSON enviado (sem campos calculados), erros `{"erro"}` e API fora do ar.
- `LembreteFormValidatorTest`, `RecorrenciaFormatterTest`: formulário e textos dos lembretes.
- `TarefasPurasTest`: `Slug`, `FiltroTarefas` (busca, prioridade, tags e projeto combinados) e `TarefaFormValidator`.
- `AppConfigTest`, `IniciadorApiTest`: localização da pasta `CLAUDE` e as partes puras do "Iniciar API" (sem chamar o Docker).

## Tela Lembretes
- **Lista** os lembretes ordenados pelo próximo aviso, com filtro **Ativos / Inativos / Todos** (consulta a API) e **busca** local que ignora maiúsculas e acentos.
- **Painel de detalhes** (à direita, pode ser ocultado) com a mensagem completa, que dá para selecionar e copiar.
- **Formulário** (Novo, Editar, Duplicar): os campos de recorrência mudam conforme o tipo (Único, Diário, Semanal, Mensal). A validação aparece no próprio campo, e um lembrete único no passado é barrado.
- **Excluir** e **Testar no Discord** pedem confirmação. No teste, o toast tem o botão "Ver texto".

| Tecla | Ação |
|---|---|
| `Ctrl+N` | novo lembrete |
| `Enter` / duplo-clique | editar o selecionado |
| `Delete` | excluir o selecionado |
| `F5` | atualizar |
| `Ctrl+F` | buscar (`Esc` limpa a busca) |

Limitações:
- **Não há ativar/desativar:** a API não tem essa rota.
- **A edição substitui o lembrete inteiro** (`PUT`). Se o Claude atualizar o mesmo lembrete depois, o texto que você editou pode ser sobrescrito.

## Tela Tarefas
- **Árvore à esquerda**, com os squads e os projetos dentro deles, mais o nó "Sem projeto", cada um com a contagem de tarefas em aberto.
  - Selecionar um **squad** mostra todas as tarefas dele; selecionar um **projeto** filtra o board.
  - Clique direito: novo projeto, nova tarefa aqui, editar, arquivar e excluir. Nos projetos, também abrir ou copiar o `contextoMd`.
  - O "+" cria um squad. "Mostrar arquivados" exibe os arquivados.
- **Board com 4 colunas:**
  - **Arraste os cards** entre colunas ou dentro da mesma coluna para reordenar; uma linha azul mostra onde o card vai cair.
  - **Soltar em Bloqueado pede o motivo.** Cancelar deixa o card onde estava.
  - **Feito** mostra só as tarefas concluídas nos últimos 14 dias. O link "Ver todas as concluídas" carrega o resto.
  - Em **A Fazer**, o campo "+ Adicionar tarefa" cria uma tarefa só com o título, no squad e projeto selecionados.
- **Card:**
  - borda colorida pela prioridade (vermelha = alta, amarela = média, cinza = baixa);
  - chip do projeto, quando a visão é do squad inteiro, e tags;
  - motivo do bloqueio em vermelho;
  - título riscado e data de conclusão quando está em Feito;
  - ícone de link, que abre a URL no navegador ou o `.md` de `docs/` no programa padrão.
- **Formulário** (Nova, Editar, Duplicar):
  - título, squad e projeto (o combo de projeto é filtrado pelo squad);
  - status, com o motivo obrigatório em Bloqueado, e prioridade;
  - tags em chips: Enter ou vírgula adiciona, e há autocomplete com as tags já usadas;
  - link, com o botão "Abrir", e descrição.
- **Filtros:** busca (título, descrição, tags, motivo e projeto, sem acento), Prioridade e Tags (seleção múltipla). O botão "Limpar filtros" aparece quando há algum ativo.
- **Lembra entre execuções:** o último squad ou projeto selecionado e a largura do painel da esquerda.

| Tecla | Ação |
|---|---|
| `Ctrl+N` | nova tarefa |
| clique / duplo-clique / `Enter` | selecionar / editar o card |
| `Ctrl+1` … `Ctrl+4` | mover o card selecionado para A Fazer / Fazendo / Bloqueado / Feito |
| `Delete` | excluir o card selecionado (com o foco no board) |
| `F5` | atualizar |
| `Ctrl+F` | buscar (`Esc` limpa a busca) |

## Estrutura
```
src/main/java/dev/interfacegrafica/
  App.java                 # ponto de entrada; lista de telas
  Launcher.java            # main do executável (não estende Application, exigência do JavaFX no classpath)
  config/AppConfig.java    # URL da api, pastas CLAUDE e docs/ e fuso (America/Sao_Paulo)
  client/                  # ApiHttp (base HTTP + Jackson), LembretesClient, TarefasClient, ApiException, dto/
  shell/                   # MainShell (menu lateral, barra superior) e a interface Tela
  lembretes/               # tela de lembretes: Tela, ViewModel, FormDialog, validador, formatador, busca
  tarefas/                 # tela de tarefas: TarefasTela, TarefasViewModel, BoardView, CardTarefa,
                           #   TarefaFormDialog, CadastroDialogs (squad/projeto/motivo), FiltroTarefas, Slug, validador
  sistema/IniciadorApi.java # botão "Iniciar API": Docker Desktop + docker compose up -d (sem JavaFX)
  ui/                      # Async, Notificacoes (toasts), Confirmacao, Tema, Logo, Links, ApiMonitor
src/main/resources/dev/interfacegrafica/   # app.css (só variáveis -color-* do AtlantaFX) e as logos
```
Regras do código:
- **Nenhuma chamada HTTP na FX thread.** Tudo passa por `Async.executar(...)`.
- `client/` não conhece JavaFX. Cada módulo da api tem um cliente que estende `ApiHttp`.
- Validação, filtros e formatação são classes puras e têm teste.
- No CSS, use **só as variáveis do tema** (`-color-fg-muted`, `-color-accent-fg`…), sem cores fixas.

## Como adicionar uma tela nova
1. Se a tela usa um módulo novo da api, crie o cliente em `client/` estendendo `ApiHttp` (veja `TarefasClient`).
2. Crie um pacote, por exemplo `dev.interfacegrafica.agenda`, com uma classe que implementa `shell.Tela`:
   ```java
   public class AgendaTela implements Tela {
       private final BorderPane raiz = new BorderPane();   // monte o conteúdo uma vez
       public AgendaTela(AgendaClient client, ApiMonitor monitor, Notificacoes notificacoes) { /* montar */ }
       @Override public String nome() { return "Agenda"; }
       @Override public Ikon icone() { return Feather.CALENDAR; }
       @Override public Node conteudo() { return raiz; }
       @Override public void aoMostrar() { /* carregar dados na 1ª vez */ }
   }
   ```
3. Registre a tela em `App.start()`, na lista `telas`. O menu lateral, o título da barra superior e a troca de conteúdo saem automaticamente.
4. Para chamar a API, use `Async.executar(trabalho, sucesso, falha)`. Mostre o retorno com `notificacoes.sucesso(...)` / `erro(...)` e informe o resultado ao `ApiMonitor` (`informarSucesso()` / `informarFalha(t)`).
5. Atalhos de teclado: registre na `Scene` e confira se a tela está visível (`raiz.getParent() != null`), como em `LembretesTela` e `TarefasTela`.
