"""
Servidor local de transcricao de audio do Kanvox (RF-05).

Roda o Whisper (via faster-whisper) na propria maquina do servidor, sem
depender de nenhuma API externa. Expoe o mesmo contrato da API de
transcricao da OpenAI/Groq (POST /v1/audio/transcriptions), entao o
ServicoTranscricaoWhisper do Spring so precisa apontar para este endereco.

Fica escutando apenas em 127.0.0.1: nao tem autenticacao propria, quem
fala com ele e so o Spring, que roda na mesma maquina.

Configuracao por variavel de ambiente:
  WHISPER_MODELO   tamanho do modelo: tiny, base, small, medium, large-v3-turbo...
                   (padrao: small — bom equilibrio entre qualidade e velocidade em CPU)
  WHISPER_THREADS  threads de CPU usadas (padrao: metade dos processadores logicos,
                   que costuma ser o numero de nucleos fisicos — usar todos nao ajuda)
  WHISPER_BEAM     largura da busca do decodificador (padrao: 1 = mais rapido;
                   5 = um pouco mais preciso e bem mais lento)
"""

import logging
import os
import threading
import time

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from faster_whisper import WhisperModel

registrador = logging.getLogger("kanvox.transcritor")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

NOME_DO_MODELO = os.getenv("WHISPER_MODELO", "small")
THREADS = int(os.getenv("WHISPER_THREADS", max(1, (os.cpu_count() or 2) // 2)))
BEAM = int(os.getenv("WHISPER_BEAM", "1"))

# O modelo e carregado uma unica vez, na subida do servidor (leva alguns
# segundos; a primeira execucao tambem baixa o modelo da internet — depois
# fica em cache no disco e o servidor funciona 100% offline).
registrador.info("Carregando o modelo Whisper '%s' (CPU, int8)...", NOME_DO_MODELO)
modelo = WhisperModel(NOME_DO_MODELO, device="cpu", compute_type="int8", cpu_threads=THREADS)
registrador.info("Modelo carregado.")

# Duas transcricoes ao mesmo tempo disputariam a mesma CPU e ficariam as duas
# lentas. Com o lock, uma espera a outra terminar.
trava = threading.Lock()

app = FastAPI(title="Transcritor do Kanvox")


@app.get("/saude")
def saude():
    return {"status": "ok", "modelo": NOME_DO_MODELO}


@app.post("/v1/audio/transcriptions")
def transcrever(
    file: UploadFile = File(...),
    language: str = Form("pt"),
    # O Spring tambem manda "model" e "response_format" (contrato da OpenAI).
    # O modelo quem escolhe e a variavel WHISPER_MODELO; a resposta e sempre JSON.
    model: str = Form(""),
    response_format: str = Form("json"),
):
    inicio = time.time()
    try:
        with trava:
            # O faster-whisper aceita o arquivo direto (webm, wav, mp3...) e
            # decodifica sozinho, sem precisar do ffmpeg instalado na maquina.
            # vad_filter corta os silencios — evita "alucinacao" do Whisper
            # (texto inventado) em trechos sem fala.
            segmentos, info = modelo.transcribe(file.file, language=language, beam_size=BEAM, vad_filter=True)
            texto = " ".join(segmento.text.strip() for segmento in segmentos).strip()
    except Exception as erro:
        registrador.exception("Falha ao transcrever o audio")
        raise HTTPException(status_code=500, detail="Falha ao transcrever o audio.") from erro

    registrador.info("Transcrito %.1fs de audio em %.1fs", info.duration, time.time() - inicio)
    return {"text": texto}
