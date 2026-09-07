# Kanvox — Estudo de migração: de SaaS aberto para servidor único

> Status: **em estudo**, decisão ainda não fechada entre Luan e Guilherme.
> Este documento registra a discussão e a arquitetura proposta, para não
> perder o raciocínio até a decisão final ser tomada.

## 1. Contexto

Hoje o Kanvox roda como uma instância única (VPS na Oracle Cloud, ver
[GUIA_DEPLOY.md](../deploy/GUIA_DEPLOY.md)), mas se comporta como um SaaS
aberto: qualquer pessoa se cadastra (`POST /api/autenticacao/cadastro`,
sem nenhuma restrição) e cria seus próprios projetos, compartilhando o
mesmo banco com todo mundo.

A ideia em estudo é tratar o Kanvox como um sistema **exclusivo de uma
única empresa**, com servidor próprio — físico, na empresa, ou uma VPS
dedicada a ela — em vez de uma plataforma multiempresa.

## 2. Por que não é um banco híbrido/bidirecional

A primeira pergunta foi se isso exigiria um banco "híbrido", com uma
cópia local e uma cópia na nuvem se sincronizando nos dois sentidos.
**Decisão: não.**

Sincronização bidirecional (cada lado aceitando escrita e depois
reconciliando) é um dos problemas mais difíceis de sistemas
distribuídos — exige resolver conflito quando o mesmo registro é
alterado nos dois lados ao mesmo tempo (ex.: a mesma tarefa movida no
servidor local sem internet e, simultaneamente, por alguém acessando
online), fila de mudanças pendentes e detecção de reconexão. Isso não
é compatível com o nível de solução simples e explicável que o Kanvox
vem seguindo (ver `CLAUDE.md`/memória do projeto).

A alternativa adotada resolve o mesmo problema sem precisar de duas
cópias do banco: **um servidor único, um PostgreSQL único, acessado por
dois caminhos diferentes** — local e online. Como é sempre o mesmo
banco, não existe conflito para resolver.

## 3. Arquitetura proposta

### 3.1 Acesso offline (rede local)

Quem está fisicamente na empresa acessa o servidor pela rede interna
(LAN) — direto pelo IP local, sem passar pela internet. **Funciona
mesmo se a internet da empresa cair**, porque o tráfego nunca sai da
rede local.

### 3.2 Acesso online (internet)

Quem está fora da empresa (home office, cliente, etc.) acessa o mesmo
servidor pela internet. Duas dificuldades práticas aqui, e a solução
escolhida resolve as duas:

- **IP dinâmico**: internet residencial/comercial comum não tem IP
  público fixo — ele muda periodicamente.
- **CGNAT**: vários provedores nem chegam a dar um IP público de
  verdade (compartilham um IP entre múltiplos clientes), o que torna
  port forward no roteador impossível, não importa a configuração.

**Solução: Cloudflare Tunnel.** Um cliente (`cloudflared`) roda no
próprio servidor e abre uma conexão de **saída** para a Cloudflare —
não precisa receber conexão de fora, então CGNAT deixa de ser um
problema, e não é preciso abrir nenhuma porta no roteador. O hostname
público fica fixo do lado da Cloudflare; o túnel se reconecta sozinho
quando o IP muda. HTTPS vem automático, sem precisar gerenciar
certificado (diferente do Caddy usado hoje na VPS, que faz isso via
Let's Encrypt).

Isso também é a opção mais segura para expor o servidor: a porta do
PostgreSQL nunca é exposta, só a aplicação (que já exige JWT), e a
borda pública fica com a segurança da Cloudflare em vez de depender só
do roteador da empresa (reforça o RNF-02 já existente no
[PROJETO_KANVOX.md](PROJETO_KANVOX.md)).

## 4. O que muda em relação ao que existe hoje

Tecnicamente, quase nada — o Kanvox nunca teve conceito de
multiempresa no código (não existe entidade "Empresa" separando dados
de clientes diferentes); a arquitetura de hoje (uma VPS, um Postgres)
já tem o mesmo formato de um servidor único.

O que de fato mudaria:

1. **Onde o servidor roda fisicamente** — trocar (ou não) a VPS de
   nuvem por uma máquina na própria empresa. É decisão de infraestrutura,
   não exige mudança no código Java.
2. **Cadastro aberto ou fechado** *(decisão pendente)* — hoje qualquer
   um se cadastra sem restrição. Rodando para uma empresa só, isso pode
   continuar aberto (só quem souber o link se cadastraria) ou ser
   restrito (ex.: só quem foi convidado por um administrador). Não
   decidido ainda.
3. **Narrativa do TG** — o pitch passa de "plataforma SaaS multiempresa"
   para "sistema de gestão de projetos para uso interno de uma
   empresa", o que também simplifica a defesa (sem multi-tenancy, sem
   cobrança por empresa, sem isolamento de dados entre clientes para
   justificar).

## 5. Ambiente de teste (prova de conceito)

> **Status: validado em 2026-09-07, com uso real.** Não só HTTP 200 —
> testado com o Kanvox de verdade (cadastro, login, quadro Kanban) do
> PC na rede local e do celular em 4G fora de casa ao mesmo tempo,
> vendo as mesmas tarefas atualizarem nos dois (ver 5.5). O que segue é
> o passo a passo que realmente funcionou, não mais um plano.

A arquitetura foi validada num notebook antigo com Debian + CasaOS já
em uso doméstico (hoje serve como servidor de música). O CasaOS é só
uma camada de dashboard sobre Docker; por baixo é um Debian normal,
com acesso de shell/apt igual a qualquer outro — deu para instalar
Java 21 e PostgreSQL nativamente, sem depender do CasaOS ou de Docker
para o Kanvox em si.

Domínio usado no teste: **`kanvox-teste.fluima.com`**, não o domínio
de produção (`kanvox.fluima.com`, que continua apontando para a VPS)
— assim o teste não derruba nem interfere no que já está no ar. O DNS
de `fluima.com` já é gerenciado pela Cloudflare, pré-requisito para o
Tunnel criar o registro automaticamente.

**Armadilha de certificado (custou a maior parte do tempo de
depuração):** o plano gratuito da Cloudflare dá um certificado
Universal SSL que cobre `fluima.com` e `*.fluima.com` — **um nível**
de subdomínio só. Um hostname de dois níveis, como
`teste.kanvox.fluima.com`, não é coberto por esse wildcard (a própria
Cloudflare avisa isso no formulário: "Multi-level subdomains require
Advanced Certificate Manager"); a borda da Cloudflare recusa o
handshake TLS com `TLS alert, handshake failure` porque não tem
certificado pra apresentar pra esse SNI. **Por isso o hostname final é
`kanvox-teste.fluima.com` (um nível só, direto sob `fluima.com`), não
`teste.kanvox.fluima.com`.** Vale lembrar essa regra para qualquer
subdomínio novo que decidirem criar sob `fluima.com` no futuro — tem
que ser de um nível só, a menos que assinem o Advanced Certificate
Manager (pago).

### 5.1 Criar o túnel pela dashboard (não pela CLI)

O jeito que funcionou na prática foi todo pela dashboard Zero Trust
(**one.dash.cloudflare.com → Networks → Tunnels → Create a tunnel**),
não pelo fluxo `cloudflared tunnel login` da CLI — mais simples
porque não depende de abrir navegador na própria máquina do notebook
(útil rodando por um terminal sem interface gráfica) e não exige criar
um `config.yml` manualmente: a Cloudflare gera um comando de
instalação com um **token** embutido, e o roteamento (`Public
Hostname`) é configurado direto na tela, sem arquivo local.

1. **Create a tunnel** → tipo **Cloudflared** → nome `kanvox-teste`.
2. Na tela seguinte ("Install and run a connector"), **antes de rodar
   o comando**, configurar o **Public Hostname**: Subdomain
   `kanvox-teste`, Domain `fluima.com`, Service Type `HTTP`, URL
   `http://localhost:8080`. Salvar — isso já cria o registro DNS.
3. Copiar o comando de instalação mostrado pra Debian 64-bit (baixa o
   pacote e roda `sudo cloudflared service install <TOKEN>`) e rodar
   no notebook. **O token é uma credencial — nunca digitar à mão nem
   colar em chat, só copiar direto da dashboard pro terminal.**

Alternativa mais simples que a instalação via repositório apt (que deu
problema de digitação manual no teste — ver histórico da sessão): se
não conseguir copiar/colar o comando oficial (com chave GPG e
repositório), baixar o `.deb` direto e instalar:

```bash
curl -fsSL https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb -o cloudflared.deb
sudo dpkg -i cloudflared.deb
```

Depois, o comando com token (`sudo cloudflared service install
eyJ...`) registra e sobe o serviço systemd sozinho — não precisa de
`config.yml` nem do `systemctl enable --now` manual, o próprio comando
já faz isso.

Confirmar com:
```bash
sudo systemctl status cloudflared
```
Precisa aparecer **active (running)** e, nos logs, 4 linhas
`Registered tunnel connection` (`connIndex=0` a `3` — redundância
normal do túnel).

### 5.2 Checar conflito de porta com o CasaOS

O CasaOS normalmente já usa a porta 80 para o próprio painel — não é
um problema para o Cloudflare Tunnel em si (ele não depende das portas
80/443 ficarem livres, ao contrário do Caddy), só relevante se algum
dia quiserem rodar Caddy também nessa máquina:

```bash
sudo ss -tlnp | grep ':80\|:443'
```

### 5.3 Instalar Java 21 e PostgreSQL

Seguir a **Etapa 6** do [GUIA_DEPLOY.md](../deploy/GUIA_DEPLOY.md)
(comandos `apt install openjdk-21-jre-headless` e
`apt install postgresql`) direto no notebook — sem instalar o Caddy,
já que o Cloudflare Tunnel cuida do HTTPS.

### 5.4 Acesso remoto ao notebook para o deploy

O terminal embutido do CasaOS (widget de SSH na própria dashboard) não
serve bem para colar comandos longos, e pediu a senha errada na
primeira tentativa (senha do painel do CasaOS ≠ senha do usuário Linux
— `sudo passwd <usuario>` resolve, se acontecer de novo). O caminho
que funcionou bem: autorizar a chave SSH pública da máquina de
desenvolvimento em `~/.ssh/authorized_keys` do notebook, e depois usar
`scp`/`ssh` normalmente a partir dela — sem digitação manual de
comandos longos no console físico do notebook (fonte da maioria dos
erros de digitação desta sessão de teste).

### 5.5 Validação funcional (não só HTTP 200)

Testado na prática em 2026-09-07: PC na rede local (`192.168.1.41:8080`)
e celular em **4G, fora da rede de casa** (`kanvox-teste.fluima.com`)
usando o Kanvox ao mesmo tempo — tarefa criada/movida em um aparece
atualizada no outro em poucos segundos (o polling do quadro, RF-03.6).
Isso confirma não só que os dois caminhos respondem, mas que os dois
batem no **mesmo banco único**, validando o ponto central desta
arquitetura (seção 2–3): um servidor, um banco, dois acessos, sem
sincronização nenhuma.

### 5.6 Deploy do JAR

Buildar o JAR na máquina de desenvolvimento (`.\mvnw.cmd clean package
-DskipTests`, mesmo comando do `deploy.ps1`) e enviar por `scp` para o
notebook, depois seguir as Etapas 7–10 do
[GUIA_DEPLOY.md](../deploy/GUIA_DEPLOY.md) (criar usuário de sistema
`kanvox`, mover o JAR e o `.env` para `/opt/kanvox`, instalar o mesmo
`kanvox.service` como serviço systemd). Sem Caddy — o Cloudflare Tunnel
já cuida do HTTPS, então o `.env` não precisa de `SERVER_ADDRESS`
restrito a `127.0.0.1` (o padrão `0.0.0.0` é o que permite o acesso
tanto local/LAN quanto pelo túnel, os dois batendo na mesma porta
8080).

### 5.7 Atualizando depois do deploy inicial

Assim como a VPS tem o `deploy.ps1`, o notebook tem o
[deploy-notebook.ps1](../deploy-notebook.ps1) — mesmo padrão (builda,
envia por `scp`, reinicia o serviço), só apontando pro IP da rede local
em vez da VPS. Rodar `.\deploy-notebook.ps1` a partir da raiz do
projeto builda e publica a versão atual com um comando só.

Duas diferenças em relação à VPS, específicas desse ambiente doméstico:

- **Acesso por chave SSH**: a chave pública da máquina de
  desenvolvimento precisa estar em `~/.ssh/authorized_keys` do usuário
  do notebook — sem isso, o `scp`/`ssh` do script ficam esperando uma
  senha que nunca chega (o script não é interativo). Se o terminal do
  CasaOS pedir senha errada na primeira tentativa, é porque a senha do
  painel do CasaOS não é a mesma do usuário Linux — `sudo passwd
  <usuario>` resolve.
- **`sudo` sem senha, só para os comandos do deploy**: como o script
  não é interativo, os comandos `sudo systemctl stop/start` e `sudo mv
  /tmp/kanvox.jar ...` remotos travariam esperando senha. A regra fica
  em `/etc/sudoers.d/kanvox-deploy`, restrita a exatamente 6 comandos
  (parar/iniciar/reiniciar/checar o serviço `kanvox`, mover o JAR pro
  lugar certo, ajustar o dono do arquivo) — o resto do `sudo` continua
  pedindo senha normalmente.

  ```
  zxc ALL=(root) NOPASSWD: /usr/bin/systemctl stop kanvox
  zxc ALL=(root) NOPASSWD: /usr/bin/systemctl start kanvox
  zxc ALL=(root) NOPASSWD: /usr/bin/systemctl restart kanvox
  zxc ALL=(root) NOPASSWD: /usr/bin/systemctl status kanvox
  zxc ALL=(root) NOPASSWD: /usr/bin/mv /tmp/kanvox.jar /opt/kanvox/kanvox.jar
  zxc ALL=(root) NOPASSWD: /usr/bin/chown kanvox\:kanvox /opt/kanvox/kanvox.jar
  ```

  **Armadilha de sintaxe do sudoers**: o caractere `:` precisa ser
  escapado (`\:`) dentro de um argumento de comando — sem isso, o
  `chown kanvox:kanvox` quebra a regra inteira com erro de sintaxe (e,
  se o arquivo já estiver instalado em `/etc/sudoers.d/` quando isso
  acontece, o `sudo` passa a recusar aquela regra especificamente,
  avisando do erro toda vez que qualquer `sudo` roda — sem travar o
  `sudo` inteiro, já que a regra de grupo padrão continua valendo, mas
  gerando bastante ruído no terminal). Antes de instalar qualquer
  regra nova, validar num arquivo temporário primeiro:
  `sudo visudo -cf /tmp/arquivo-de-teste` — só copiar pra
  `/etc/sudoers.d/` depois de ver `parsed OK`/`análise OK`.

## 6. Checklist de segurança

Rodar num notebook doméstico e expor parte do acesso pela internet muda
o perfil de risco em relação à VPS de hoje — vale reforçar alguns
pontos além do RNF-02 já existente no
[PROJETO_KANVOX.md](PROJETO_KANVOX.md#6-requisitos-não-funcionais-rnf).

### Essencial

- [ ] **PostgreSQL só escuta em `localhost`** — nunca na rede, nem
      local nem externa. Só a aplicação Java (na mesma máquina) fala
      com o banco. Conferir com `sudo ss -tlnp | grep 5432`: precisa
      aparecer `127.0.0.1:5432`, nunca `0.0.0.0:5432`. Se precisar
      ajustar, é no `listen_addresses` do `postgresql.conf`.
- [ ] **Firewall (`ufw`) no notebook** — bloquear tudo por padrão e
      liberar só o necessário: porta 8080 apenas para a rede local
      (`ufw allow from 192.168.0.0/24 to any port 8080`), nenhuma
      porta de banco exposta nem para a LAN. O Cloudflare Tunnel não
      precisa de porta de entrada liberada — a conexão é sempre de
      saída, então o resto da máquina fica fechado.
- [ ] **Senhas fortes e únicas** para o usuário `kanvox` do Postgres e
      para `CHAVE_JWT` — nunca reaproveitar os valores padrão de
      desenvolvimento que já existem no `application.properties`.
- [ ] **Atualização automática de segurança do Debian**
      (`unattended-upgrades`) — servidor doméstico não tem alguém
      olhando todo dia; patch de segurança precisa ser automático.

### Recomendado

- [ ] **Cloudflare Access na frente do túnel** — antes de chegar no
      login do Kanvox, a Cloudflare pode exigir uma segunda camada
      (ex.: código por e-mail) para quem tenta acessar
      `teste.kanvox.fluima.com`. Gratuito no plano free, configurado
      direto no painel Zero Trust da Cloudflare, sem mexer no código
      do Kanvox.
- [ ] **Backup automático do banco, fora dessa máquina** — o ponto
      mais importante que muda ao sair de VPS para notebook
      doméstico: se o HD falhar, o notebook for roubado ou pegar uma
      pane elétrica, os dados da empresa somem. Um `pg_dump` diário +
      envio para outro lugar (outro disco físico, ou um Drive/S3
      gratuito) resolve.
- [ ] **Usuário systemd dedicado sem shell** — o `kanvox.service`
      atual já faz isso (`User=kanvox`, shell `nologin`); manter esse
      padrão também no ambiente de teste. Se alguém comprometer a
      aplicação, não ganha shell na máquina.

### Só se expuserem SSH para a internet

Hoje o plano é só LAN + Cloudflare Tunnel para a aplicação, sem SSH
exposto — mas se isso mudar no futuro:

- [ ] Autenticação só por chave, nunca por senha
      (`PasswordAuthentication no` no `sshd_config`).
- [ ] `fail2ban` para banir IP depois de tentativas de login falhas
      repetidas.

## 7. Requisitos de hardware para produção real

O notebook doméstico (seção 5) é **apenas simulação/prova de conceito**
— valida a arquitetura (acesso local + túnel), não é o que rodaria numa
empresa de verdade. Para um cenário real, a documentação do TG precisa
cobrir os requisitos de hardware abaixo.

### Essencial

- [ ] **Máquina dedicada** — não precisa ser servidor de rack de
      datacenter; um mini-PC ou desktop robusto já atende, já que o
      Kanvox é leve (Spring Boot + PostgreSQL não exigem muito
      hardware). Dimensionar pelo número real de usuários simultâneos
      esperado da empresa, sem superdimensionar à toa.
- [ ] **Nobreak (UPS)** — protege contra queda de energia e picos de
      tensão. Idealmente com um serviço como o NUT (Network UPS Tools)
      configurado para desligar a máquina graciosamente quando a
      bateria estiver acabando, evitando corrupção do banco por queda
      abrupta no meio de uma escrita.
- [ ] **Roteador/modem também no nobreak** — de nada adianta o
      servidor ficar de pé se a internet cai junto na falta de energia.
- [ ] **Backup automático fora da máquina** (reforça a seção 6) — mais
      crítico ainda com hardware físico real: disco pode falhar,
      máquina pode ser roubada ou danificada.

### Recomendado (citar como melhoria futura, evitar over-engineering para o nível do TG)

- [ ] **RAID espelhado (RAID 1)** nos discos — evita perda de dados por
      falha de um único disco, sem depender só do backup.
- [ ] **Internet redundante** (linha secundária 4G/5G como failover) —
      relevante principalmente pela confiabilidade do webhook do
      WhatsApp (seção sobre integrações futuras); é o item mais
      avançado da lista.
- [ ] **Local físico apropriado** (sala fechada, ventilada) — mais
      operacional do que técnico.

## 8. Decisões em aberto

- [ ] Servidor físico na empresa vs. manter (ou duplicar) a VPS
- [ ] Cadastro aberto vs. fechado por convite
- [ ] Se seguir adiante: formalizar como RNF novo em
      [PROJETO_KANVOX.md](PROJETO_KANVOX.md) (seção 6) e ajustar a
      redação dos RF que hoje pressupõem cadastro livre
