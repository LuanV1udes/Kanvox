package br.edu.fatec.kanvox.servico.transcricao;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import br.edu.fatec.kanvox.servico.RegraDeNegocioExcecao;

/**
 * Implementacao atual do servico de transcricao: modelo Whisper acessado
 * por HTTP, no formato da API da OpenAI (POST .../audio/transcriptions).
 * Por padrao aponta para o transcritor local (pasta transcritor/), que roda
 * na mesma maquina do servidor e nao depende de internet. Como o formato e
 * o mesmo, tambem funciona com a API da Groq — basta trocar a URL, o modelo
 * e informar a chave (variavel de ambiente TRANSCRICAO_CHAVE_API, RNF-02).
 */
@Service
public class ServicoTranscricaoWhisper implements ServicoTranscricao {

	private static final Logger registrador = LoggerFactory.getLogger(ServicoTranscricaoWhisper.class);

	private final RestTemplate restTemplate;
	private final String chaveApi;
	private final String url;
	private final String modelo;

	public ServicoTranscricaoWhisper(
			@Value("${kanvox.transcricao.chave-api}") String chaveApi,
			@Value("${kanvox.transcricao.url}") String url,
			@Value("${kanvox.transcricao.modelo}") String modelo) {
		this.chaveApi = chaveApi;
		this.url = url;
		this.modelo = modelo;
		// limites de tempo: a transcricao de ate 1 minuto de audio deve
		// responder em ate 15 segundos (RNF-06.1); acima de 30s desistimos
		SimpleClientHttpRequestFactory fabricaDeRequisicoes = new SimpleClientHttpRequestFactory();
		fabricaDeRequisicoes.setConnectTimeout(5000);
		fabricaDeRequisicoes.setReadTimeout(30000);
		this.restTemplate = new RestTemplate(fabricaDeRequisicoes);
	}

	@Override
	public String transcrever(byte[] audio, String nomeDoArquivo) {
		try {
			HttpHeaders cabecalhos = new HttpHeaders();
			cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
			// o transcritor local nao pede chave; so provedores externos exigem
			if (chaveApi != null && !chaveApi.isBlank()) {
				cabecalhos.setBearerAuth(chaveApi);
			}

			// ByteArrayResource com nome de arquivo: e assim que a API identifica o formato do audio
			ByteArrayResource arquivoDeAudio = new ByteArrayResource(audio) {
				@Override
				public String getFilename() {
					return nomeDoArquivo == null ? "audio.webm" : nomeDoArquivo;
				}
			};

			MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
			corpo.add("file", arquivoDeAudio);
			corpo.add("model", modelo);
			corpo.add("language", "pt");
			corpo.add("response_format", "json");

			Map<?, ?> resposta = restTemplate.postForObject(url, new HttpEntity<>(corpo, cabecalhos), Map.class);
			Object texto = resposta == null ? null : resposta.get("text");
			if (texto == null || texto.toString().isBlank()) {
				throw new RegraDeNegocioExcecao(
						"A transcricao voltou vazia. Verifique se o audio tem fala audivel e tente novamente.");
			}
			return texto.toString().trim();

		} catch (RestClientException e) {
			// RNF-03: o detalhe tecnico vai para o log; o usuario recebe um aviso
			// amigavel e pode gerar o relatorio sem a parte narrada (RF-05.4)
			registrador.error("Falha ao chamar o servico de transcricao (Whisper)", e);
			throw new RegraDeNegocioExcecao(
					"Nao foi possivel transcrever o audio agora. Tente novamente ou gere o relatorio sem a narracao.");
		}
	}

}
