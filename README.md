# Kanvox

Sistema web de gerenciamento de projetos com transcrição de áudio para relatórios (Whisper).
Trabalho de Graduação — Fatec Ourinhos, ADS. O documento completo do projeto está em [docs/PROJETO_KANVOX.md](docs/PROJETO_KANVOX.md).

## Requisitos para rodar

- **[Java 21 (JDK)](https://adoptium.net/)** — confira com `java -version`; precisa ser o JDK (não só o JRE), porque o Maven compila o projeto. O Maven em si já vem embutido pelo wrapper `mvnw`, não precisa instalar separado.
- **[PostgreSQL](https://www.postgresql.org/download/)** local (o projeto foi feito e testado na versão 18) — ver seção [Banco de dados](#banco-de-dados) abaixo.
- **[Git](https://git-scm.com/downloads)** para clonar o repositório.
- **[VS Code](https://code.visualstudio.com/)** (opcional, mas é o editor usado no projeto) — ver seção [Configurar o VS Code](#configurar-o-vs-code) abaixo.
- **(Opcional) SMTP para recuperação de senha** — sem isso, o "Esqueceu sua senha?" ainda funciona (gera o token normalmente), só não envia o e-mail de verdade; o erro fica só no log (degradação graciosa). Para enviar de verdade, gere uma ["senha de app"](https://myaccount.google.com/apppasswords) no Gmail (não a senha normal da conta) e defina como mostrado abaixo.

## Banco de dados

1. Instale o PostgreSQL (link acima) — no instalador do Windows, o pgAdmin já vem junto. Anote a senha que você definir para o superusuário `postgres` durante a instalação.
2. Confira se o serviço está rodando: abra `services.msc` (Win+R → digite `services.msc`) e procure por algo como **`postgresql-x64-18`** — o status precisa estar "Em execução". Se não estiver, clique com o botão direito → Iniciar.
3. Crie o usuário e o banco **dedicados ao projeto** (feito uma única vez) — abra o pgAdmin, conecte no servidor local como `postgres`, clique com o botão direito no banco `postgres` → **Query Tool**, e rode:

   ```sql
   CREATE USER kanvox WITH PASSWORD 'kanvox';
   CREATE DATABASE kanvox OWNER kanvox;
   ```

   (o mesmo funciona pelo `psql` no terminal, se preferir linha de comando em vez do pgAdmin)

4. Pronto — a aplicação se conecta por padrão com `kanvox` / `kanvox` (usuário e banco dedicados, nenhuma senha pessoal do PostgreSQL fica no código). As tabelas são criadas sozinhas na primeira vez que a aplicação sobe (`spring.jpa.hibernate.ddl-auto=update`), não precisa rodar nenhum script de schema.

## Configurar o VS Code

O projeto já traz um `.vscode/extensions.json` — ao abrir a pasta pela primeira vez, o VS Code mostra um aviso "This workspace has extension recommendations" com um botão **Install All**. Se não aparecer, instale manualmente pela aba Extensions (`Ctrl+Shift+X`), buscando por:

| Extensão | ID | Para quê |
|---|---|---|
| Extension Pack for Java | `vscjava.vscode-java-pack` | Suporte a Java: autocompletar, navegação, debugger e o runner de testes (JUnit) |
| Spring Boot Extension Pack | `vmware.vscode-boot-dev-pack` | Autocompletar de `application.properties`, navegação nos endpoints REST e o painel **Spring Boot Dashboard** |

Ou pelo terminal, de uma vez:

```
code --install-extension vscjava.vscode-java-pack
code --install-extension vmware.vscode-boot-dev-pack
```

Depois de instaladas, o VS Code leva um tempinho **indexando o projeto** (barra de progresso no canto inferior direito) antes de os comandos abaixo aparecerem — é normal na primeira vez.

**Rodar a aplicação pelo VS Code** (sem digitar nada no terminal): abra `src/main/java/br/edu/fatec/kanvox/KanvoxAplicacao.java` e clique em **Run** (aparece flutuando acima de `public static void main`) — ou abra o painel **Spring Boot Dashboard** na barra lateral esquerda e clique no ▶ ao lado de `kanvox`. Pra debugar (com breakpoints), use **Debug** no mesmo lugar em vez de Run, ou `F5`.

**Rodar os testes pelo VS Code**: abra a aba **Testing** (ícone de frasco na barra lateral esquerda) e clique em **Run Tests** no topo pra rodar tudo — ou clique no ▶ verde que aparece do lado de cada `@Test`/classe, direto no editor, pra rodar só um teste.

Essas duas opções fazem exatamente o mesmo que os comandos de terminal (`mvnw spring-boot:run` e `mvnw test`) mostrados mais abaixo — use o que for mais confortável.

### Credenciais (arquivo `.env`)

Nenhuma credencial fica no código (RNF-02) — todas vêm de variáveis de ambiente, lidas de um arquivo `.env` na raiz do projeto (o próprio `KanvoxAplicacao` carrega ele automaticamente ao iniciar). Esse arquivo **nunca é commitado** (está no `.gitignore`).

Copie o modelo e preencha com os seus valores:

```
copy .env.example .env
```

| Variável | Obrigatória? | Descrição |
|---|---|---|
| `BANCO_URL`, `BANCO_USUARIO`, `BANCO_SENHA` | Não | Só se usar um banco diferente do padrão `kanvox`/`kanvox` |
| `CHAVE_JWT` | Não em dev, sim em produção | Chave de assinatura dos tokens JWT |
| `GROQ_API_KEY` | Só para transcrição de áudio | Chave gratuita gerada em [console.groq.com](https://console.groq.com) |
| `SMTP_USUARIO`, `SMTP_SENHA` | Só para envio real de e-mail | Conta e senha de app do Gmail |

## Como rodar a aplicação

Pelo terminal (na raiz do projeto) — ou use o botão **Run** do VS Code, explicado acima:

```
.\mvnw.cmd spring-boot:run
```

Abra **http://localhost:8080** no navegador — a tela de login/cadastro aparece. O frontend é HTML/CSS/JS puro, servido pelo próprio Spring Boot a partir de `src/main/resources/static/` (sem build, sem npm — a única biblioteca é o SortableJS, um arquivo local em `js/sortable.min.js`).

**Telas:** login/cadastro (`index.html`) → lista de projetos (`projetos.html`) → página do projeto (`projeto.html`) com o quadro Kanban (drag-and-drop, atualizado por polling a cada 5s), membros, notificações (sino no topo) e relatórios com narração por voz (gravação pelo microfone → transcrição → revisão → salvar).

## Como colocar em produção (VPS + HTTPS)

Guia completo passo a passo — criação da VPS gratuita na Oracle Cloud, DNS, systemd e Caddy (HTTPS automático) — em [`deploy/GUIA_DEPLOY.md`](deploy/GUIA_DEPLOY.md).

## Como rodar os testes

Pelo terminal — ou use a aba **Testing** do VS Code, explicada acima:

```
.\mvnw.cmd test
```

Os testes usam o banco em memória H2 — **não precisam de PostgreSQL instalado**.

**Nota sobre mudanças em enums:** o `ddl-auto=update` do Hibernate cria colunas novas automaticamente, mas **não atualiza** a restrição `CHECK` de uma coluna quando um enum Java ganha um valor novo (ex.: ao adicionar `EM_REVISAO` em `StatusTarefa`). Se isso acontecer com um banco que já existe, o sintoma é um erro 500 do tipo `violates check constraint`. Solução: `ALTER TABLE <tabela> DROP CONSTRAINT <tabela>_<coluna>_check;` no psql/pgAdmin — o Hibernate recria a constraint certa na próxima inicialização.

## Endpoints já implementados

### Autenticação (público)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/autenticacao/cadastro` | Cria conta — corpo: `{ "nome", "email", "senha" }` |
| POST | `/api/autenticacao/login` | Login — corpo: `{ "email", "senha" }`; devolve `{ "token" }` |
| POST | `/api/autenticacao/esqueci-senha` | Corpo: `{ "email" }`. Sempre responde com a mesma mensagem genérica (evita revelar quais e-mails estão cadastrados) |
| POST | `/api/autenticacao/redefinir-senha` | Corpo: `{ "token", "novaSenha" }`. Token vem do link recebido por e-mail, válido por 1h e utilizável uma única vez |
| GET | `/api/autenticacao/token-recuperacao/{token}` | Devolve `{ "email" }` da conta associada a um token válido — usado pela tela de redefinição para mostrar de qual conta a senha está sendo trocada |

### Projetos e membros (exigem `Authorization: Bearer <token>`)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/projetos` | Cria projeto — corpo: `{ "nome", "descricao" }`; quem cria vira Gestor |
| GET | `/api/projetos` | Lista os projetos em que o usuário logado participa |
| GET | `/api/projetos/{id}` | Visão geral: dados, membros, progresso, tarefas em aberto |
| PUT | `/api/projetos/{id}` | Edita nome/descrição (somente Gestor) |
| PUT | `/api/projetos/{id}/encerrar` | Encerra o projeto (somente Gestor) |
| GET | `/api/projetos/{id}/membros` | Lista os membros ativos e os convites pendentes |
| POST | `/api/projetos/{id}/membros` | Convida usuário já cadastrado — corpo: `{ "email", "papel": "MEMBRO" ou "OBSERVADOR" }` (somente Gestor). O convite nasce **pendente**: só dá acesso depois que o convidado aceita |
| DELETE | `/api/projetos/{id}/membros/{usuarioId}` | Remove membro ou cancela convite pendente (somente Gestor) |
| POST | `/api/projetos/{id}/sair` | O usuário logado sai do projeto (Gestor não pode sair) |

### Convites recebidos (exigem `Authorization: Bearer <token>`)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/convites` | Lista os convites pendentes do usuário logado |
| POST | `/api/convites/{id}/aceitar` | Aceita o convite — o usuário passa a participar do projeto |
| POST | `/api/convites/{id}/recusar` | Recusa o convite |

### Quadro Kanban (exigem `Authorization: Bearer <token>`)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/projetos/{id}/tarefas` | Lista as tarefas do projeto (endpoint do polling do quadro) |
| POST | `/api/projetos/{id}/tarefas` | Cria tarefa (somente Gestor) — corpo: `{ "titulo", "descricao", "prazo": "2026-08-01", "prioridade": "BAIXA"\|"MEDIA"\|"ALTA", "responsavel": { "id": 2 } }` (prazo, prioridade e responsável opcionais) |
| PUT | `/api/tarefas/{id}` | Edita título, descrição, prazo, prioridade e responsável (**somente Gestor** — o Membro não edita dados, nem das próprias tarefas) |
| PUT | `/api/tarefas/{id}/status` | Move de coluna — corpo: `{ "status": "A_FAZER" \| "EM_ANDAMENTO" \| "BLOQUEADO" \| "EM_REVISAO" \| "CONCLUIDO" }`. Concluir ou reabrir uma tarefa é exclusivo do Gestor — o Membro entrega movendo para "Em Revisão" |
| DELETE | `/api/tarefas/{id}` | Exclui tarefa (somente Gestor) |

**Permissões no Kanban:** somente o Gestor cria, edita o conteúdo e exclui tarefas, e só ele conclui ou reabre; Membro **apenas movimenta** (arrasta) as tarefas atribuídas a ele entre as colunas — não edita título/descrição/prazo/prioridade/responsável, mesmo das próprias tarefas; Observador só visualiza. Projeto encerrado fica somente leitura.

### Comentários e anexos na tarefa — a devolutiva (exigem `Authorization: Bearer <token>`)

| Método | Rota | Descrição |
|---|---|---|
| GET / POST | `/api/tarefas/{id}/comentarios` | Lista ou escreve um comentário — corpo: `{ "texto" }` (Gestor e Membro; Observador só lê) |
| DELETE | `/api/comentarios/{id}` | Exclui um comentário (autor ou Gestor) |
| GET / POST | `/api/tarefas/{id}/anexos` | Lista ou envia um anexo (`multipart/form-data`, campo `arquivo`, limite 10MB) — Gestor em qualquer tarefa, Membro só nas atribuídas a ele |
| GET | `/api/anexos/{id}/download` | Baixa o arquivo |
| DELETE | `/api/anexos/{id}` | Exclui um anexo (quem enviou ou Gestor) |

### Notificações (exigem `Authorization: Bearer <token>`)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/notificacoes` | Lista as notificações do usuário logado (mais recentes primeiro) |
| PUT | `/api/notificacoes/{id}/lida` | Marca uma notificação como lida |
| PUT | `/api/notificacoes/lidas` | Marca todas como lidas |

**Quando as notificações são geradas:** tarefa atribuída a você por outra pessoa; tarefa do seu projeto marcada como Bloqueado ou movida para Em Revisão (avisa o Gestor); tarefa com prazo vencido (avisa o Gestor — rotina automática a cada 30 minutos); novo comentário numa tarefa (avisa o Gestor e o responsável, exceto quem comentou); convite recebido, e convite aceito/recusado (avisa o Gestor).

### Relatórios e transcrição de áudio (exigem `Authorization: Bearer <token>`, somente Gestor gera/transcreve)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/projetos/{id}/transcricoes` | Envia um áudio (`multipart/form-data`, campo `audio`) e recebe `{ "texto": "..." }` para revisar |
| POST | `/api/projetos/{id}/relatorios` | Gera um relatório do estado atual do projeto — corpo opcional: `{ "transcricaoAudio": "texto já revisado" }` |
| GET | `/api/projetos/{id}/relatorios` | Lista os relatórios do projeto (qualquer membro pode ler) |

**Configuração necessária:** defina a variável de ambiente `GROQ_API_KEY` com uma chave gratuita gerada em [console.groq.com](https://console.groq.com). Sem ela, a transcrição responde com um erro amigável — o resto do sistema continua funcionando normalmente (RNF-03).

**Fluxo com revisão humana (RF-05.5):** o Gestor grava o áudio → chama `/transcricoes` e recebe o texto → revisa/edita na tela → chama `/relatorios` enviando o texto já revisado. A transcrição nunca é salva automaticamente.
