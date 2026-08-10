# Kanvox

Sistema web de gerenciamento de projetos com transcrição de áudio para relatórios (Whisper).
Trabalho de Graduação — Fatec Ourinhos, ADS. O documento completo do projeto está em [docs/PROJETO_KANVOX.md](docs/PROJETO_KANVOX.md).

## Requisitos para rodar

- **Java 21** (já basta — o Maven vem embutido pelo wrapper `mvnw`)
- **PostgreSQL** local com o usuário e o banco do projeto. Setup feito **uma única vez** — no pgAdmin (Query Tool) ou no psql, conectado como superusuário `postgres`, execute:

```sql
CREATE USER kanvox WITH PASSWORD 'kanvox';
CREATE DATABASE kanvox OWNER kanvox;
```

A aplicação se conecta por padrão com `kanvox` / `kanvox` (usuário e banco dedicados ao projeto — nenhuma senha pessoal fica no código). Para usar outros valores, defina as variáveis de ambiente `BANCO_URL`, `BANCO_USUARIO`, `BANCO_SENHA`.

## Como rodar a aplicação

```
.\mvnw.cmd spring-boot:run
```

Abra **http://localhost:8080** no navegador — a tela de login/cadastro aparece. O frontend é HTML/CSS/JS puro, servido pelo próprio Spring Boot a partir de `src/main/resources/static/` (sem build, sem npm — a única biblioteca é o SortableJS, um arquivo local em `js/sortable.min.js`).

**Telas:** login/cadastro (`index.html`) → lista de projetos (`projetos.html`) → página do projeto (`projeto.html`) com o quadro Kanban (drag-and-drop, atualizado por polling a cada 5s), membros, notificações (sino no topo) e relatórios com narração por voz (gravação pelo microfone → transcrição → revisão → salvar).

## Como rodar os testes

```
.\mvnw.cmd test
```

Os testes usam o banco em memória H2 — **não precisam de PostgreSQL instalado**.

## Endpoints já implementados

### Autenticação (público)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/autenticacao/cadastro` | Cria conta — corpo: `{ "nome", "email", "senha" }` |
| POST | `/api/autenticacao/login` | Login — corpo: `{ "email", "senha" }`; devolve `{ "token" }` |

### Projetos e membros (exigem `Authorization: Bearer <token>`)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/projetos` | Cria projeto — corpo: `{ "nome", "descricao" }`; quem cria vira Gestor |
| GET | `/api/projetos` | Lista os projetos em que o usuário logado participa |
| GET | `/api/projetos/{id}` | Visão geral: dados, membros, progresso, tarefas em aberto |
| PUT | `/api/projetos/{id}` | Edita nome/descrição (somente Gestor) |
| PUT | `/api/projetos/{id}/encerrar` | Encerra o projeto (somente Gestor) |
| GET | `/api/projetos/{id}/membros` | Lista os membros ativos |
| POST | `/api/projetos/{id}/membros` | Convida usuário já cadastrado — corpo: `{ "email", "papel": "MEMBRO" ou "OBSERVADOR" }` (somente Gestor) |
| DELETE | `/api/projetos/{id}/membros/{usuarioId}` | Remove membro (somente Gestor) |
| POST | `/api/projetos/{id}/sair` | O usuário logado sai do projeto (Gestor não pode sair) |

### Quadro Kanban (exigem `Authorization: Bearer <token>`)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/projetos/{id}/tarefas` | Lista as tarefas do projeto (endpoint do polling do quadro) |
| POST | `/api/projetos/{id}/tarefas` | Cria tarefa — corpo: `{ "titulo", "descricao", "prazo": "2026-08-01", "responsavel": { "id": 2 } }` (prazo e responsável opcionais; Membro sempre cria para si) |
| PUT | `/api/tarefas/{id}` | Edita título, descrição, prazo e responsável (reatribuir é só Gestor) |
| PUT | `/api/tarefas/{id}/status` | Move de coluna — corpo: `{ "status": "A_FAZER" \| "EM_ANDAMENTO" \| "BLOQUEADO" \| "CONCLUIDO" }` |
| DELETE | `/api/tarefas/{id}` | Exclui tarefa (somente Gestor) |

**Permissões no Kanban:** somente o Gestor cria e exclui tarefas; Membro edita/move apenas as tarefas atribuídas a ele; Observador só visualiza. Projeto encerrado fica somente leitura.

### Notificações (exigem `Authorization: Bearer <token>`)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/notificacoes` | Lista as notificações do usuário logado (mais recentes primeiro) |
| PUT | `/api/notificacoes/{id}/lida` | Marca uma notificação como lida |
| PUT | `/api/notificacoes/lidas` | Marca todas como lidas |

**Quando as notificações são geradas:** tarefa atribuída a você por outra pessoa (na criação ou reatribuição); tarefa do seu projeto marcada como Bloqueado (avisa o Gestor); tarefa com prazo vencido (avisa o Gestor — verificada por uma rotina automática a cada 30 minutos).

### Relatórios e transcrição de áudio (exigem `Authorization: Bearer <token>`, somente Gestor gera/transcreve)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/projetos/{id}/transcricoes` | Envia um áudio (`multipart/form-data`, campo `audio`) e recebe `{ "texto": "..." }` para revisar |
| POST | `/api/projetos/{id}/relatorios` | Gera um relatório do estado atual do projeto — corpo opcional: `{ "transcricaoAudio": "texto já revisado" }` |
| GET | `/api/projetos/{id}/relatorios` | Lista os relatórios do projeto (qualquer membro pode ler) |

**Configuração necessária:** defina a variável de ambiente `GROQ_API_KEY` com uma chave gratuita gerada em [console.groq.com](https://console.groq.com). Sem ela, a transcrição responde com um erro amigável — o resto do sistema continua funcionando normalmente (RNF-03).

**Fluxo com revisão humana (RF-05.5):** o Gestor grava o áudio → chama `/transcricoes` e recebe o texto → revisa/edita na tela → chama `/relatorios` enviando o texto já revisado. A transcrição nunca é salva automaticamente.
