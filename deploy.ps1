# ============================================================
# deploy.ps1 - builda o Kanvox e envia a versao atual para a VPS
# de producao, reiniciando o servico la.
#
# Uso: .\deploy.ps1
#
# Ajuste $ChaveSsh se estiver rodando de outra maquina (ex. do
# Guilherme) - o caminho da chave privada e local de cada um.
#
# Pre-requisito: a VPS ja precisa estar configurada seguindo
# deploy/GUIA_DEPLOY.md (servico kanvox instalado via systemd).
# ============================================================

$ErrorActionPreference = "Stop"

$ChaveSsh = "C:\Users\LuanZ\Downloads\ssh-key-2026-08-18.key"
$UsuarioVps = "ubuntu"
$IpVps = "157.151.25.25"
$JarLocal = "target\kanvox-0.0.1-SNAPSHOT.jar"

Write-Host "Buildando o JAR..." -ForegroundColor Cyan
.\mvnw.cmd clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build falhou, deploy abortado." -ForegroundColor Red
    exit 1
}

Write-Host "Enviando o JAR para a VPS..." -ForegroundColor Cyan
scp -i $ChaveSsh $JarLocal "${UsuarioVps}@${IpVps}:/tmp/kanvox.jar"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Envio falhou, deploy abortado." -ForegroundColor Red
    exit 1
}

Write-Host "Reiniciando o servico na VPS..." -ForegroundColor Cyan
ssh -i $ChaveSsh "${UsuarioVps}@${IpVps}" "sudo systemctl stop kanvox && sudo mv /tmp/kanvox.jar /opt/kanvox/kanvox.jar && sudo chown kanvox:kanvox /opt/kanvox/kanvox.jar && sudo systemctl start kanvox && sudo systemctl status kanvox --no-pager"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Falha ao reiniciar o servico na VPS." -ForegroundColor Red
    exit 1
}

Write-Host "Deploy concluido." -ForegroundColor Green
