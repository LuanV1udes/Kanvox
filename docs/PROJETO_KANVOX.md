# Kanvox — Documento de Projeto

> Documento de contexto para desenvolvimento com Claude Code.
> Última atualização: julho/2026 — decisões técnicas fechadas: perfis por projeto, frontend em HTML/CSS/JS puro, JWT sem refresh token, transcrição com revisão obrigatória.

---

## 1. O que é o Kanvox

O Kanvox é um sistema web de gerenciamento de projetos. O problema que ele resolve: equipes de projeto normalmente têm uma defasagem entre o trabalho que de fato acontece e o que está registrado nas ferramentas de gestão, porque essas ferramentas exigem um passo extra que as pessoas não têm disciplina de manter atualizado.

A proposta do Kanvox é oferecer uma plataforma de gestão de projetos completa (Kanban, CRUD de projetos e tarefas, perfis de acesso) com um diferencial de IA aplicada: o gestor pode **narrar um resumo em áudio** e o sistema **transcreve automaticamente** esse áudio para compor relatórios do projeto, usando o modelo Whisper.

Trabalho de Graduação — Fatec Ourinhos, curso de Análise e Desenvolvimento de Sistemas.
Autores: Guilherme Lino Mariano e Luan Marcelo Viudes Evangelista.

---

## 2. Estado atual e decisão de escopo

O projeto passou pela banca de qualificação (nota 8,0). A banca apontou que o escopo original — bot de WhatsApp com interpretação de mensagens em tempo real, transcrição de áudio integrada ao bot, comandos de voz do gestor, e relatórios — equivalia a **"quase quatro trabalhos diferentes"** para dois alunos no prazo disponível.

**Decisão tomada:** desacoplar o bot de WhatsApp do núcleo do sistema. A integração com WhatsApp (Evolution API, webhook, interpretação de mensagens em tempo real) foi identificada como o maior risco técnico do projeto — exigiria lidar com infraestrutura externa instável, processamento assíncrono e complexidade de interpretação de linguagem natural em cima de um canal de terceiros.

O módulo de transcrição de áudio (Whisper) **não foi removido** — foi **redesenhado como funcionalidade independente da plataforma web**, sem depender do bot. Isso mantém um diferencial de IA real no projeto, com risco técnico muito menor.

### O que fica no núcleo (escopo principal, garantido)

- Autenticação e perfis de acesso (Gestor, Membro, Observador)
- Gerenciamento de projetos (CRUD)
- Quadro Kanban (CRUD de tarefas, 4 colunas)
- Geração de relatórios de projeto
- Transcrição de áudio para relatórios (Whisper) — o gestor grava um áudio na própria plataforma, o sistema transcreve e o texto compõe o relatório

### O que vira trabalho futuro (fora do escopo desta entrega)

- Bot integrado ao WhatsApp (Evolution API)
- Interpretação de mensagens de texto/áudio enviadas no grupo do WhatsApp
- Comandos de voz do gestor (Web Speech API) para delegar tarefas ou gerar relatórios falando
- Notificação automática via WhatsApp

---

## 3. Stack tecnológica

A stack foi escolhida priorizando tecnologias consolidadas e de baixo acoplamento a serviços de terceiros — evitando dependência de plataformas "da moda" (ex. Supabase, Vercel) em favor de ferramentas amplamente usadas em nível institucional e de mercado.

| Camada | Tecnologia | Observação |
|---|---|---|
| Frontend | HTML/CSS/JS puro (sem framework, sem build) | Servido pelo próprio Spring Boot a partir de `resources/static`; SortableJS para drag-and-drop; MediaRecorder para gravação de áudio |
| Backend | **Java + Spring Boot** | Arquitetura em camadas (Controlador → Serviço → Repositório) |
| Banco de dados | **PostgreSQL** | Local em desenvolvimento; administrado via **pgAdmin** |
| ORM | Spring Data JPA (Hibernate) | Mapeamento objeto-relacional padrão do ecossistema Spring |
| Autenticação | Spring Security + JWT | Token único com expiração (sem refresh token no MVP); RBAC validado na camada de serviço |
| Transcrição de áudio | Whisper via API Groq | Chamada HTTP simples (RestTemplate/WebClient), gratuita em dev e produção |
| Atualização do Kanban | Polling automático (frontend consulta o backend a cada poucos segundos) | Ver nota abaixo |

**Nota sobre a atualização do Kanban (RF-03.6):** para manter a stack simples e evitar a complexidade de um servidor WebSocket dedicado, o MVP usa **polling automático** — o frontend busca o estado atualizado das tarefas periodicamente (ex. a cada 5 segundos). Isso atende ao requisito de visualização atualizada sem exigir infraestrutura de tempo real. Uma implementação com WebSocket (Spring WebSocket/STOMP) pode ser registrada como melhoria futura, caso sobre tempo no cronograma.

**Nota sobre o frontend:** a escolha por HTML/CSS/JS puro é deliberada. O Kanban com drag-and-drop, o polling e a gravação de áudio são implementados com recursos nativos do navegador (`fetch`, MediaRecorder) mais uma única biblioteca de arquivo único (SortableJS, incluída como arquivo local em `static/`, sem CDN nem gerenciador de pacotes). Não há etapa de build nem transpilação — o frontend é aberto direto pelo navegador, servido pelo Spring Boot, o que mantém o projeto simples de rodar e de explicar linha a linha na banca. O áudio gravado pelo MediaRecorder (formato WebM/Opus) é aceito diretamente pela API da Groq, sem necessidade de conversão.

**Nota sobre vendor lock-in:** a chamada ao serviço de transcrição deve ficar isolada atrás de uma interface no backend — ex. uma interface `ServicoTranscricao` com um método `transcrever(byte[] audio): String`, implementada por uma classe `ServicoTranscricaoGroqWhisper`. Isso permite trocar de provedor de IA no futuro sem alterar o restante do sistema.

**Nota sobre contingência:** se o banco de dados ou o serviço de transcrição ficarem indisponíveis, o sistema deve degradar graciosamente — CRUD e Kanban continuam funcionando normalmente (não dependem da IA), e a funcionalidade de transcrição deve falhar de forma isolada, avisando o usuário sem quebrar o restante da aplicação.

**Sobre o PostgreSQL e o pgAdmin:** o banco roda como instância PostgreSQL padrão (local durante o desenvolvimento, podendo ser hospedado em uma VPS gratuita como a Oracle Cloud Always Free em produção). O pgAdmin é usado apenas como ferramenta de administração visual do banco — não faz parte da aplicação em si, é uma ferramenta de apoio ao desenvolvimento.

**Sobre hospedagem e HTTPS em produção:** aplicação e banco rodam na mesma VPS gratuita (Oracle Cloud Always Free). O HTTPS exigido pelo RNF-02 é resolvido colocando o servidor **Caddy** na frente do Spring Boot — ele obtém e renova certificados Let's Encrypt automaticamente, sem configuração manual de TLS. Durante o desenvolvimento local, HTTP simples é suficiente.

**Sobre o idioma do código:** todo o projeto — nomes de pastas, pacotes, classes, métodos e variáveis — é escrito em português, sem misturar com inglês. Isso facilita a leitura por parte da banca e mantém o código mais próximo da terminologia usada no próprio documento do TG. Acentos são evitados em nomes de pastas, pacotes e identificadores (classes, métodos, variáveis) por segurança de codificação entre sistemas operacionais — por exemplo, usa-se `servico` em vez de `serviço`, `relatorio` em vez de `relatório`.

---

## 4. Perfis de usuário

Os perfis são definidos **por projeto**, no vínculo entre usuário e projeto (campo `papelNoProjeto` da entidade `MembroProjeto`) — **não existe papel global de usuário**. Um mesmo usuário pode ser Gestor em um projeto e Membro em outro. Qualquer usuário cadastrado pode criar um projeto e torna-se automaticamente o Gestor dele.

| Perfil | Permissões |
|---|---|
| **Gestor** | Acesso completo: cria projetos, tarefas, convida membros, edita qualquer tarefa, gera relatórios (com ou sem transcrição de áudio), remove membros |
| **Membro** | Acessa apenas os projetos em que está inserido; edita apenas as tarefas atribuídas a ele; pode sair de um projeto voluntariamente |
| **Observador** | Acesso somente leitura ao projeto e ao Kanban |

---

## 5. Requisitos Funcionais (RF)

### RF-01 — Autenticação e Perfis de Acesso
- RF-01.1 — Cadastro e login via e-mail e senha
- RF-01.2 — Suporte a três perfis **por projeto**: Gestor, Membro, Observador (o papel pertence ao vínculo usuário–projeto, não ao usuário)
- RF-01.3 — Recuperação de senha via e-mail (link com expiração de 1h). Implementação simples: token e validade armazenados no próprio registro do usuário (dois campos, sem tabela extra), envio via Spring Mail + SMTP gratuito (ex. Gmail). *Se o cronograma apertar, este item é rebaixado a desejável — é o único RF que depende de envio de e-mail.*
- RF-01.4 — Gestor pode convidar membros para um projeto (**apenas usuários já cadastrados**, localizados por e-mail; convite a e-mails sem conta fica como trabalho futuro)
- RF-01.5 — Gestor pode remover membros; Membro pode sair voluntariamente de um projeto (em ambos os casos o vínculo `MembroProjeto` é marcado como inativo, nunca excluído — é isso que preserva o histórico, sem tabela de auditoria)

### RF-02 — Gerenciamento de Projetos
- RF-02.1 — Gestor cria projetos (nome, descrição)
- RF-02.2 — Gestor edita e encerra projetos existentes
- RF-02.3 — Visão geral do projeto: progresso, tarefas em aberto, membros, histórico

### RF-03 — Quadro Kanban
- RF-03.1 — Quadro com 4 colunas: A Fazer, Em Andamento, Bloqueado, Concluído
- RF-03.2 — Gestor cria/edita/exclui qualquer tarefa do projeto (a criação e a exclusão de tarefas são exclusivas do Gestor)
- RF-03.3 — Membro edita e movimenta apenas as tarefas atribuídas a ele (não cria tarefas)
- RF-03.4 — Movimentação de tarefas via drag-and-drop
- RF-03.5 — Cada tarefa contém: título, descrição, **um** responsável, prazo, status (um único responsável por tarefa no MVP; múltiplos responsáveis exigiriam tabela de junção extra e ficam como melhoria futura)
- RF-03.6 — Atualização periódica do quadro via polling automático, refletindo mudanças para todos os membros conectados em poucos segundos

### RF-04 — Relatórios
- RF-04.1 — Geração automática de relatório a partir do estado atual das tarefas
- RF-04.2 — Relatório contém: progresso geral, tarefas concluídas no período, tarefas em aberto, impedimentos, próximos prazos
- RF-04.3 — Relatório pode ser solicitado pelo Gestor a qualquer momento pela plataforma web
- RF-04.4 — Relatório é visualizável dentro da plataforma (exportação em PDF é desejável, não obrigatória no MVP)

### RF-05 — Transcrição de Áudio para Relatórios
- RF-05.1 — Gestor pode gravar um áudio diretamente na plataforma web
- RF-05.2 — Sistema transcreve o áudio via Whisper
- RF-05.3 — O texto transcrito é incorporado ao relatório do projeto como observação narrada
- RF-05.4 — Em caso de falha na transcrição, o sistema deve informar o erro sem impedir a geração do restante do relatório
- RF-05.5 — A transcrição é **sempre exibida ao Gestor para revisão e edição antes de ser salva** no relatório — o sistema nunca assume a transcrição como correta automaticamente (human-in-the-loop)

### RF-06 — Notificações
- RF-06.1 — Gestor recebe notificação de tarefas marcadas como "Bloqueado"
- RF-06.2 — Gestor recebe notificação de tarefas com prazo vencido
- RF-06.3 — Membro recebe notificação de tarefas recém-atribuídas
- RF-06.4 — Notificações exibidas dentro da plataforma web

**Nota de implementação:** a detecção de tarefas com prazo vencido (RF-06.2) é feita por uma rotina agendada do próprio Spring (`@Scheduled`), rodando periodicamente (ex. a cada 30 minutos) para criar as notificações — sem infraestrutura externa. As notificações são buscadas pelo frontend no mesmo polling do Kanban.

---

## 6. Requisitos Não Funcionais (RNF)

### RNF-01 — Desempenho
- Páginas carregam em menos de 3s em conexão de 10 Mbps
- Atualização do Kanban refletida em até 5 segundos via polling automático (sem exigir ação manual do usuário para atualizar a tela)

### RNF-02 — Segurança
- RBAC implementado via Spring Security, validado na camada de serviço (não apenas na interface)
- Toda comunicação via HTTPS/TLS
- Token JWT único com expiração (ex. 8 horas); ao expirar, o usuário faz login novamente — sem mecanismo de refresh token no MVP (simplificação deliberada)
- Senhas armazenadas com hash (BCrypt)
- Chaves de API e credenciais do banco em variáveis de ambiente, nunca em código-fonte

### RNF-03 — Disponibilidade e Confiabilidade
- Falha no serviço de transcrição não deve bloquear o restante da aplicação (fallback: sistema notifica o erro e permite gerar o relatório sem a parte narrada)
- Logs de erro das integrações externas para diagnóstico

### RNF-04 — Escalabilidade
- Arquitetura permite extração do backend para serviço independente se necessário
- Chamada de transcrição isolada atrás de uma camada de abstração (facilita troca de provedor)

### RNF-05 — Usabilidade
- Interface responsiva (desktop e tablet)
- Componentes com atributos de acessibilidade (ARIA) básicos
- Mensagens de erro amigáveis, sem detalhes técnicos expostos ao usuário final

### RNF-06 — Inteligência Artificial *(bloco novo, apontado pela banca)*
- RNF-06.1 — Tempo máximo de resposta da transcrição: até 15 segundos para áudio de até 1 minuto
- RNF-06.2 — Qualidade da transcrição: como não é possível medir acurácia automaticamente sem um texto de referência, o controle de qualidade é **humano** — toda transcrição passa por revisão e edição do Gestor antes de ser salva (ver RF-05.5). A referência de mercado do Whisper (98–99% de acurácia em condições favoráveis) é citada no artigo como contexto, não como métrica medida pelo sistema
- RNF-06.3 — Disponibilidade do serviço de IA deve ser monitorada; indisponibilidade não pode derrubar o restante do sistema (ver RNF-03)

### RNF-07 — Custo de Operação
- Custo mensal em escala acadêmica não deve ultrapassar R$ 5,00
- Todas as integrações externas devem operar dentro de planos gratuitos durante o desenvolvimento

---

## 7. Modelo de dados (entidades principais)

Nomes de tabelas, classes e campos em português, sem acentuação, seguindo a convenção adotada no projeto.

- **Usuario** — id, nome, email, senha (hash), tokenRecuperacao (opcional), tokenExpiraEm (opcional). *Sem campo de papel global* — o papel é sempre por projeto, em MembroProjeto
- **Projeto** — id, nome, descricao, criadoPor, status, criadoEm
- **MembroProjeto** — projetoId, usuarioId, papelNoProjeto (gestor/membro/observador), ativo (booleano — vira `false` quando o membro sai ou é removido; o registro nunca é excluído)
- **Tarefa** — id, projetoId, titulo, descricao, responsavelId, prazo, status (kanban), criadoEm
- **Relatorio** — id, projetoId, conteudo, transcricaoAudio (opcional), geradoPor, geradoEm
- **Notificacao** — id, usuarioId, tipo, mensagem, lida (booleano), criadoEm

---

## 8. Estrutura de pastas sugerida

A estrutura segue o padrão em camadas clássico do Spring Boot (Controlador → Serviço → Repositório), sem frameworks adicionais de arquitetura — mantendo o projeto simples de navegar e de explicar na banca. Todos os nomes de pastas, pacotes e classes estão em português, sem acentos, para evitar problemas de codificação e manter consistência com o restante do documento.

```
kanvox/
├── src/
│   ├── main/
│   │   ├── java/br/edu/fatec/kanvox/
│   │   │   ├── controlador/          # Recebe requisições HTTP (endpoints)
│   │   │   │   ├── AutenticacaoControlador.java
│   │   │   │   ├── ProjetoControlador.java
│   │   │   │   ├── TarefaControlador.java
│   │   │   │   └── RelatorioControlador.java
│   │   │   │
│   │   │   ├── servico/              # Regras de negócio
│   │   │   │   ├── AutenticacaoServico.java
│   │   │   │   ├── ProjetoServico.java
│   │   │   │   ├── TarefaServico.java
│   │   │   │   ├── RelatorioServico.java
│   │   │   │   └── transcricao/
│   │   │   │       ├── ServicoTranscricao.java            # interface (abstração)
│   │   │   │       └── ServicoTranscricaoGroqWhisper.java  # implementação atual
│   │   │   │
│   │   │   ├── repositorio/          # Acesso ao banco (Spring Data JPA)
│   │   │   │   ├── UsuarioRepositorio.java
│   │   │   │   ├── ProjetoRepositorio.java
│   │   │   │   ├── TarefaRepositorio.java
│   │   │   │   └── RelatorioRepositorio.java
│   │   │   │
│   │   │   ├── modelo/               # Entidades (tabelas do banco)
│   │   │   │   ├── Usuario.java
│   │   │   │   ├── Projeto.java
│   │   │   │   ├── Tarefa.java
│   │   │   │   ├── Relatorio.java
│   │   │   │   └── Notificacao.java
│   │   │   │
│   │   │   ├── config/               # Configurações (segurança, etc.)
│   │   │   │   └── ConfiguracaoSeguranca.java
│   │   │   │
│   │   │   └── KanvoxAplicacao.java  # classe principal (main)
│   │   │
│   │   └── resources/
│   │       ├── application.properties   # configuração do banco, porta, etc.
│   │       └── static/                  # frontend (HTML/CSS/JS puro + SortableJS local)
│   │
│   └── test/
│       └── java/br/edu/fatec/kanvox/    # testes unitários (JUnit)
│
├── pom.xml                          # dependências do projeto (Maven)
└── docs/
    └── PROJETO_KANVOX.md            # este arquivo
```

**Por que essa organização é simples de explicar:** cada requisição HTTP entra pelo `controlador`, que delega a lógica para o `servico`, que usa o `repositorio` para conversar com o banco. Nenhuma camada pula a outra — o controlador nunca acessa o repositório diretamente, por exemplo. Essa é a arquitetura em camadas ensinada na maioria dos cursos de ADS e é o padrão esperado em um TG de nível institucional.

**Simplificação intencional:** o projeto não usa camada de DTO (objetos de transferência separados das entidades) nem padrões de projeto avançados. As próprias classes do pacote `modelo` são usadas diretamente nas respostas da API. Essa escolha é proposital: reduz a quantidade de código e de conceitos que a dupla precisa explicar e defender na banca, mantendo o projeto no nível de complexidade adequado para um TG de graduação.

Essa simplificação exige **três cuidados obrigatórios**: (1) o campo `senha` de `Usuario` deve ser somente-escrita no JSON (`@JsonProperty(access = WRITE_ONLY)`): o cliente consegue enviar a senha no cadastro, mas o hash nunca aparece nas respostas da API; (2) as relações entre entidades devem ser unidirecionais (ou anotadas com `@JsonIgnore` no lado inverso), para evitar recursão infinita na serialização; (3) a camada de serviço nunca deve confiar em campos sensíveis vindos do corpo da requisição (ex. `papelNoProjeto`) — esses valores são sempre definidos pelo próprio serviço, conforme a regra de negócio.

---

## 9. Cronograma de desenvolvimento (referência)

| Mês | Etapa |
|---|---|
| Jul | Ajustes pós-qualificação, setup do repositório |
| Jul–Ago | Backend: API REST, banco de dados, autenticação |
| Ago–Set | Frontend: Kanban e CRUD de projetos/tarefas |
| Set | Módulo de relatórios + transcrição de áudio (Whisper) |
| Set–Out | Testes funcionais |
| Set–Nov | Validação com usuários (3 meses) |
| Nov | Redação final do artigo, ajustes finais |
| Dez | Defesa do TG |

---

## 10. Diretrizes para o desenvolvimento assistido por IA (Claude Code)

- Seguir os RF/RNF acima como fonte da verdade para o que implementar no MVP. Não implementar bot de WhatsApp, comandos de voz ou qualquer funcionalidade listada como "trabalho futuro" na seção 2, a menos que explicitamente solicitado.
- **Todo o código deve ser escrito em português** — nomes de pastas, pacotes, classes, métodos, variáveis, e mensagens de erro exibidas ao usuário. Não misturar com inglês (evitar, por exemplo, `getUsuario()` — usar `buscarUsuario()` ou `obterUsuario()`).
- Não usar acentos em nomes de pastas, pacotes, classes, métodos e variáveis (usar `servico`, `relatorio`, `criacao`, em vez de `serviço`, `relatório`, `criação`) — evita problemas de codificação entre sistemas operacionais e ferramentas.
- Respeitar a arquitetura em camadas: Controlador → Serviço → Repositório. O Controlador nunca deve acessar o Repositório diretamente, nem conter lógica de negócio.
- A chamada ao Whisper deve sempre passar pela interface `ServicoTranscricao`, nunca ser chamada diretamente do Controlador ou de outro Serviço.
- Toda operação de escrita deve validar permissão de perfil (RBAC) na camada de Serviço, antes de qualquer alteração no banco.
- Priorizar simplicidade: este é um TG de nível institucional, com prazo definido, feito por alunos — não um produto comercial. Evitar padrões de projeto avançados, camadas extras (como DTO) ou abstrações desnecessárias. O código deve ser simples o suficiente para a dupla explicar linha a linha na banca.
- Usar Spring Data JPA para todo o acesso ao banco — evitar SQL nativo, exceto em casos justificados.
- Ao gerar código, seguir exatamente a estrutura de pastas da seção 8, incluindo o pacote `br.edu.fatec.kanvox`.
- Comentários no código também devem ser escritos em português.
- Testes com JUnit devem cobrir pelo menos o RF-03 (Kanban) e o RF-05 (transcrição), que são os pontos de maior risco técnico remanescente.
