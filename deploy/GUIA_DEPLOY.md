# Guia de Deploy do Kanvox — Oracle Cloud Always Free + Caddy

Este guia coloca o Kanvox no ar, com HTTPS de verdade, dentro do plano
gratuito da Oracle Cloud (RNF-07: custo de operação próximo de zero).
Siga as etapas na ordem — cada uma depende da anterior.

---

## Etapa 1 — Criar a conta na Oracle Cloud

1. Acesse **https://www.oracle.com/cloud/free/** e clique em "Comece gratuitamente".
2. Preencha os dados (e-mail, senha, país). A Oracle pede um cartão de
   crédito para verificação de identidade — **não é cobrado** dentro do
   plano Always Free, desde que você não migre para recursos pagos.
3. Confirme o e-mail e o telefone quando solicitado.
4. Escolha a **home region** (região onde os recursos gratuitos ficam
   disponíveis) — uma vez escolhida, não muda depois. `São Paulo` ou
   `Ashburn` costumam ser as mais próximas/estáveis.

## Etapa 2 — Criar a instância (VM)

1. No painel da Oracle Cloud, vá em **Menu ☰ → Compute → Instances**.
2. Clique em **Create Instance**.
3. Nome: `kanvox-vps` (ou o que preferir).
4. Em **Image and shape**:
   - Imagem: **Canonical Ubuntu** (a versão LTS mais recente disponível).
   - Shape: clique em "Change shape" → aba **Ampere (ARM)** → escolha
     **VM.Standard.A1.Flex**, com **1 OCPU** e **6 GB de memória** — isso
     ainda está dentro do Always Free (a Oracle libera até 4 OCPUs/24GB
     em ARM de graça). Se preferir x86, o `VM.Standard.E2.1.Micro`
     também é Always Free, mas é bem mais fraco.
5. Em **Networking**: deixe a VCN padrão ser criada automaticamente.
   Marque **"Assign a public IPv4 address"**.
6. Em **Add SSH keys**: escolha "Generate a key pair for me" e
   **baixe a chave privada** (`ssh-key-*.key`) — sem ela você não
   consegue entrar na VPS depois. Guarde num lugar seguro (nunca no
   repositório do projeto).
7. Clique em **Create**. Em 1–2 minutos a instância fica "Running" e
   mostra o **IP público**.

## Etapa 3 — Abrir as portas 80 e 443

A Oracle Cloud tem **dois firewalls** — os dois precisam liberar as
portas, ou o Caddy fica inacessível de fora mesmo estando no ar.

**a) Security List (firewall da nuvem):**
1. Na página da instância, clique no link da **VCN** (embaixo, em
   "Primary VNIC" → "Subnet").
2. Abra a **Security List** padrão → **Add Ingress Rules**.
3. Adicione duas regras, ambas com "Source CIDR" = `0.0.0.0/0`:
   - Porta de destino `80` (HTTP)
   - Porta de destino `443` (HTTPS)

**b) iptables (firewall dentro da própria VM Ubuntu da Oracle):**

Conecte por SSH (veja a Etapa 4) e rode:

```bash
sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## Etapa 4 — Conectar via SSH

No seu computador (PowerShell):

```powershell
ssh -i "C:\caminho\para\ssh-key-....key" ubuntu@SEU_IP_PUBLICO
```

(No Windows pode ser necessário `icacls` para restringir a permissão
do arquivo da chave antes do SSH aceitar usá-la — se der erro de
"permissões da chave", peça ajuda que resolvemos juntos.)

## Etapa 5 — Apontar o domínio para a VPS

No painel do seu provedor de domínio, crie um registro **A** apontando
para o IP público da instância:

| Tipo | Nome | Valor |
|---|---|---|
| A | `@` (ou o subdomínio escolhido, ex. `app`) | `SEU_IP_PUBLICO` |

A propagação do DNS pode levar de alguns minutos a algumas horas.
Teste com `nslookup seudominio.com` até o IP aparecer certo.

## Etapa 6 — Instalar as dependências na VPS

Já conectado por SSH:

```bash
sudo apt update && sudo apt upgrade -y

# Java 21
sudo apt install -y openjdk-21-jre-headless

# PostgreSQL
sudo apt install -y postgresql
sudo -u postgres psql -c "CREATE USER kanvox WITH PASSWORD 'ESCOLHA_UMA_SENHA_FORTE';"
sudo -u postgres psql -c "CREATE DATABASE kanvox OWNER kanvox;"

# Caddy (repositorio oficial)
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install -y caddy
```

## Etapa 7 — Preparar o usuário e a pasta da aplicação

```bash
sudo useradd --system --create-home --shell /usr/sbin/nologin kanvox
sudo mkdir -p /opt/kanvox
sudo chown kanvox:kanvox /opt/kanvox
```

## Etapa 8 — Build do JAR (na sua máquina) e envio para a VPS

Na sua máquina, dentro da pasta do projeto:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Isso gera `target\kanvox-0.0.1-SNAPSHOT.jar`. Envie para a VPS:

```powershell
scp -i "C:\caminho\para\ssh-key-....key" target\kanvox-0.0.1-SNAPSHOT.jar ubuntu@SEU_IP_PUBLICO:/tmp/kanvox.jar
```

Na VPS:

```bash
sudo mv /tmp/kanvox.jar /opt/kanvox/kanvox.jar
sudo chown kanvox:kanvox /opt/kanvox/kanvox.jar
```

## Etapa 9 — Criar o `.env` de produção na VPS

```bash
sudo nano /opt/kanvox/.env
```

Cole (usando os mesmos nomes de `.env.example`, com os valores reais):

```
BANCO_URL=jdbc:postgresql://localhost:5432/kanvox
BANCO_USUARIO=kanvox
BANCO_SENHA=ESCOLHA_UMA_SENHA_FORTE
CHAVE_JWT=GERE_UMA_CHAVE_ALEATORIA_LONGA_AQUI
GROQ_API_KEY=sua-chave-da-groq
SMTP_USUARIO=seuemail@gmail.com
SMTP_SENHA=sua-senha-de-app-do-gmail
KANVOX_URL_BASE=https://seudominio.com
SERVER_ADDRESS=127.0.0.1
```

`KANVOX_URL_BASE` precisa ser o domínio real com `https://` — é usado para
montar o link de redefinição de senha enviado por e-mail (RF-01.3).
`SERVER_ADDRESS=127.0.0.1` faz o Java escutar só localmente, deixando o
Caddy como o único serviço exposto às portas 80/443 (mais seguro).

Depois:

```bash
sudo chown kanvox:kanvox /opt/kanvox/.env
sudo chmod 600 /opt/kanvox/.env
```

## Etapa 10 — Instalar o serviço do Kanvox

Copie o arquivo `deploy/kanvox.service` deste repositório para a VPS
(via `scp`, igual ao JAR), depois:

```bash
sudo mv /tmp/kanvox.service /etc/systemd/system/kanvox.service
sudo systemctl daemon-reload
sudo systemctl enable --now kanvox
sudo systemctl status kanvox
```

Se `status` mostrar "active (running)", a aplicação subiu. Para ver
os logs: `sudo journalctl -u kanvox -f`.

## Etapa 11 — Configurar o Caddy

1. No arquivo `deploy/Caddyfile` deste repositório, troque
   `SEU_DOMINIO_AQUI` pelo seu domínio de verdade.
2. Envie para a VPS e instale:

```bash
sudo mv /tmp/Caddyfile /etc/caddy/Caddyfile
sudo systemctl restart caddy
sudo systemctl status caddy
```

O Caddy busca o certificado Let's Encrypt sozinho na primeira
requisição ao domínio — não precisa configurar nada além do domínio
no Caddyfile.

## Etapa 12 — Testar

Acesse `https://seudominio.com` no navegador. Deve aparecer a tela de
login do Kanvox, com o cadeado de HTTPS válido.

---

## Atualizando a aplicação depois de mudanças no código

```powershell
.\mvnw.cmd clean package -DskipTests
scp -i "chave.key" target\kanvox-0.0.1-SNAPSHOT.jar ubuntu@SEU_IP:/tmp/kanvox.jar
```

Na VPS:

```bash
sudo systemctl stop kanvox
sudo mv /tmp/kanvox.jar /opt/kanvox/kanvox.jar
sudo chown kanvox:kanvox /opt/kanvox/kanvox.jar
sudo systemctl start kanvox
```

Ou, de forma automatizada, rode `.\deploy.ps1` (na raiz do projeto) —
ele builda o JAR, envia por `scp` e reinicia o serviço na VPS, tudo
em um comando só. Ajuste o caminho da chave SSH (`$ChaveSsh`) dentro
do script se estiver rodando de outra máquina.
