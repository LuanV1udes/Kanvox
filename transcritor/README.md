# Transcritor local do Kanvox

Servidor pequeno (Python) que roda o **Whisper** na própria máquina, para o Kanvox transcrever a narração dos relatórios (RF-05) **sem depender de nenhuma API externa**. Usa a biblioteca [faster-whisper](https://github.com/SYSTRAN/faster-whisper) em CPU (int8) — não precisa de placa de vídeo nem de ffmpeg.

Ele fala o mesmo formato da API de transcrição da OpenAI/Groq (`POST /v1/audio/transcriptions`), por isso o Spring (`ServicoTranscricaoWhisper`) só precisa apontar para `http://127.0.0.1:8001`, que já é o padrão.

## Instalação (uma vez)

Precisa de Python 3.10 ou mais novo. Dentro desta pasta (`transcritor/`):

```
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt      # Windows
.venv/bin/pip install -r requirements.txt          # Linux
```

## Rodando

```
.venv\Scripts\python -m uvicorn servidor:app --host 127.0.0.1 --port 8001      # Windows
.venv/bin/uvicorn servidor:app --host 127.0.0.1 --port 8001                    # Linux
```

Na **primeira vez** ele baixa o modelo `small` (~470 MB) da internet e guarda em cache no disco. Depois disso funciona totalmente offline. Para conferir que subiu: abra http://127.0.0.1:8001/saude.

Se o servidor não estiver rodando, o resto do Kanvox continua funcionando; só a transcrição responde com um aviso amigável (RNF-03).

## Configuração (variáveis de ambiente, todas opcionais)

| Variável | Padrão | O que faz |
|---|---|---|
| `WHISPER_MODELO` | `small` | Tamanho do modelo: `tiny`, `base`, `small`, `medium`, `large-v3-turbo`. Maior = mais preciso e mais lento |
| `WHISPER_THREADS` | metade dos processadores lógicos | Threads de CPU. O ideal é o número de núcleos físicos |
| `WHISPER_BEAM` | `1` | Largura da busca do decodificador. `5` = um pouco mais preciso, mais lento |

## Desempenho medido

Notebook com Ryzen 7 5700U (8 núcleos, sem GPU), modelo `small`, 8 threads: áudio de **59 s transcrito em ~11 s** — dentro do limite de 15 s do RNF-06.1. Em máquinas mais fracas, use `WHISPER_MODELO=base` (mais rápido, porém com mais erros) — vale medir no servidor real antes de decidir.

O Whisper processa o áudio em janelas de 30 s, então um áudio curto (ex. 10 s) leva quase o mesmo tempo de um de 30 s.

## No servidor (Debian)

Ver [`deploy/kanvox-transcritor.service`](../deploy/kanvox-transcritor.service) e a Etapa 9b do [GUIA_DEPLOY.md](../deploy/GUIA_DEPLOY.md).
