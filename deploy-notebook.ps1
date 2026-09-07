# ============================================================
# deploy-notebook.ps1 - builda o Kanvox e envia a versao atual para o
# notebook doméstico (ambiente de teste da arquitetura de servidor
# único, ver docs/MIGRACAO_SERVIDOR_UNICO.md), reiniciando o servico
# la. Mesmo padrao do deploy.ps1 (VPS de producao), apontando pro
# notebook na rede local.
#
# Uso: .\deploy-notebook.ps1
#
# Pre-requisito: chave SSH publica ja autorizada em
# ~/.ssh/authorized_keys do notebook, e a regra sudoers restrita em
# /etc/sudoers.d/kanvox-deploy (ver secao 5.4/5.6 do
# MIGRACAO_SERVIDOR_UNICO.md) liberando sem senha so os comandos de
# restart do Kanvox.
# ============================================================

$ErrorActionPreference = "Stop"

$ChaveSsh = "$env:USERPROFILE\.ssh\id_ed25519"
$UsuarioNotebook = "zxc"
$IpNotebook = "192.168.1.41"
$JarLocal = "target\kanvox-0.0.1-SNAPSHOT.jar"

Write-Host "Buildando o JAR..." -ForegroundColor Cyan
.\mvnw.cmd clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build falhou, deploy abortado." -ForegroundColor Red
    exit 1
}

Write-Host "Enviando o JAR para o notebook..." -ForegroundColor Cyan
scp -i $ChaveSsh $JarLocal "${UsuarioNotebook}@${IpNotebook}:/tmp/kanvox.jar"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Envio falhou, deploy abortado." -ForegroundColor Red
    exit 1
}

Write-Host "Reiniciando o servico no notebook..." -ForegroundColor Cyan
ssh -i $ChaveSsh "${UsuarioNotebook}@${IpNotebook}" "sudo systemctl stop kanvox && sudo mv /tmp/kanvox.jar /opt/kanvox/kanvox.jar && sudo chown kanvox:kanvox /opt/kanvox/kanvox.jar && sudo systemctl start kanvox && sudo systemctl status kanvox"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Falha ao reiniciar o servico no notebook." -ForegroundColor Red
    exit 1
}

Write-Host "Deploy concluido." -ForegroundColor Green
