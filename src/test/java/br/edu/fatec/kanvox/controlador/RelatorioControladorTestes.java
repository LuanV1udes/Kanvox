package br.edu.fatec.kanvox.controlador;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import br.edu.fatec.kanvox.servico.RegraDeNegocioExcecao;
import br.edu.fatec.kanvox.servico.transcricao.ServicoTranscricao;

/**
 * Testes dos relatorios (RF-04) e da transcricao (RF-05) — modulo com
 * cobertura obrigatoria segundo o documento do projeto.
 * A transcricao usa uma implementacao FALSA (abaixo), para os testes nao
 * dependerem de internet nem da API da Groq — e exatamente para permitir
 * essa troca que existe a interface ServicoTranscricao (RNF-04).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RelatorioControladorTestes.ConfiguracaoDeTranscricaoFalsa.class)
class RelatorioControladorTestes {

	private static final String TEXTO_TRANSCRITO_DE_TESTE = "O projeto avancou bem nesta semana.";

	@TestConfiguration
	static class ConfiguracaoDeTranscricaoFalsa {
		@Bean
		@Primary
		ServicoTranscricao servicoTranscricaoFalso() {
			return (audio, nomeDoArquivo) -> {
				// simula uma falha do provedor quando o arquivo se chama "falha.webm"
				if ("falha.webm".equals(nomeDoArquivo)) {
					throw new RegraDeNegocioExcecao(
							"Nao foi possivel transcrever o audio agora. Tente novamente ou gere o relatorio sem a narracao.");
				}
				return TEXTO_TRANSCRITO_DE_TESTE;
			};
		}
	}

	@Autowired
	private MockMvc mockMvc;

	private String cadastrarELogar(String nome, String email) throws Exception {
		mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"123456\"}"));
		String resposta = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"senha\":\"123456\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.token");
	}

	private long criarProjeto(String token, String nome) throws Exception {
		String resposta = mockMvc.perform(post("/api/projetos")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\"}"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	private long criarTarefa(String token, long projetoId, String titulo) throws Exception {
		String resposta = mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"" + titulo + "\"}"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	@Test
	void relatorioRefleteOEstadoAtualDasTarefas() throws Exception {
		String token = cadastrarELogar("Gestor Relatorio", "gestor.relatorio@teste.com");
		long projetoId = criarProjeto(token, "Projeto Relatorio");
		long tarefaConcluida = criarTarefa(token, projetoId, "Configurar banco de dados");
		criarTarefa(token, projetoId, "Construir quadro Kanban");

		mockMvc.perform(put("/api/tarefas/" + tarefaConcluida + "/status")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONCLUIDO\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/projetos/" + projetoId + "/relatorios")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.conteudo").value(containsString("PROGRESSO GERAL: 50%")))
				.andExpect(jsonPath("$.conteudo").value(containsString("Configurar banco de dados")))
				.andExpect(jsonPath("$.conteudo").value(containsString("Construir quadro Kanban")))
				.andExpect(jsonPath("$.transcricaoAudio").doesNotExist());
	}

	@Test
	void transcricaoRevisadaEIncorporadaAoRelatorio() throws Exception {
		String token = cadastrarELogar("Gestor Narracao", "gestor.narracao@teste.com");
		long projetoId = criarProjeto(token, "Projeto Narrado");

		// 1. o Gestor envia o audio e recebe o texto para revisar (RF-05.5)
		MockMultipartFile audio = new MockMultipartFile("audio", "gravacao.webm",
				"audio/webm", "bytes-de-audio-simulados".getBytes());
		String respostaTranscricao = mockMvc.perform(multipart("/api/projetos/" + projetoId + "/transcricoes")
				.file(audio)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.texto").value(TEXTO_TRANSCRITO_DE_TESTE))
				.andReturn().getResponse().getContentAsString();
		String textoRevisado = JsonPath.read(respostaTranscricao, "$.texto") + " (revisado pelo gestor)";

		// 2. o texto revisado entra no relatorio como observacao narrada (RF-05.3)
		mockMvc.perform(post("/api/projetos/" + projetoId + "/relatorios")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"transcricaoAudio\":\"" + textoRevisado + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.transcricaoAudio").value(textoRevisado));
	}

	@Test
	void falhaNaTranscricaoNaoImpedeORelatorio() throws Exception {
		String token = cadastrarELogar("Gestor Contingencia", "gestor.contingencia@teste.com");
		long projetoId = criarProjeto(token, "Projeto Contingencia");

		// a transcricao falha com uma mensagem amigavel (RF-05.4 / RNF-03)
		MockMultipartFile audioComFalha = new MockMultipartFile("audio", "falha.webm",
				"audio/webm", "bytes-quaisquer".getBytes());
		mockMvc.perform(multipart("/api/projetos/" + projetoId + "/transcricoes")
				.file(audioComFalha)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value(containsString("Nao foi possivel transcrever")));

		// mesmo assim o relatorio e gerado normalmente, sem a narracao
		mockMvc.perform(post("/api/projetos/" + projetoId + "/relatorios")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.conteudo").value(containsString("RELATORIO DO PROJETO")));
	}

	@Test
	void somenteGestorGeraETranscreveMasMembroPodeLer() throws Exception {
		String tokenGestor = cadastrarELogar("Gestor Leitor", "gestor.leitor@teste.com");
		String tokenMembro = cadastrarELogar("Membro Leitor", "membro.leitor@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Leitura Relatorio");
		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"membro.leitor@teste.com\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isCreated());
		// o membro aceita o convite para entrar no projeto (RF-01.4)
		String convites = mockMvc.perform(get("/api/convites")
				.header("Authorization", "Bearer " + tokenMembro))
				.andReturn().getResponse().getContentAsString();
		long conviteId = ((Number) JsonPath.read(convites, "$[0].id")).longValue();
		mockMvc.perform(post("/api/convites/" + conviteId + "/aceitar")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk());

		// membro nao gera relatorio nem transcreve (RF-04.3 / RF-05.1)
		mockMvc.perform(post("/api/projetos/" + projetoId + "/relatorios")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isForbidden());
		MockMultipartFile audio = new MockMultipartFile("audio", "gravacao.webm",
				"audio/webm", "bytes".getBytes());
		mockMvc.perform(multipart("/api/projetos/" + projetoId + "/transcricoes")
				.file(audio)
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isForbidden());

		// mas pode ler os relatorios gerados (RF-04.4)
		mockMvc.perform(post("/api/projetos/" + projetoId + "/relatorios")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isCreated());
		mockMvc.perform(get("/api/projetos/" + projetoId + "/relatorios")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void baixarPdfDoRelatorioFuncionaParaGestorEMembro() throws Exception {
		String tokenGestor = cadastrarELogar("Gestor Pdf", "gestor.pdf@teste.com");
		String tokenMembro = cadastrarELogar("Membro Pdf", "membro.pdf@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Pdf");
		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"membro.pdf@teste.com\",\"papel\":\"MEMBRO\"}"))
				.andExpect(status().isCreated());
		String convites = mockMvc.perform(get("/api/convites")
				.header("Authorization", "Bearer " + tokenMembro))
				.andReturn().getResponse().getContentAsString();
		long conviteId = ((Number) JsonPath.read(convites, "$[0].id")).longValue();
		mockMvc.perform(post("/api/convites/" + conviteId + "/aceitar")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk());

		String respostaRelatorio = mockMvc.perform(post("/api/projetos/" + projetoId + "/relatorios")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long relatorioId = ((Number) JsonPath.read(respostaRelatorio, "$.id")).longValue();

		// o Gestor baixa o PDF: confere o cabecalho de download e a assinatura "%PDF" do arquivo
		byte[] pdf = mockMvc.perform(get("/api/relatorios/" + relatorioId + "/pdf")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition",
						"attachment; filename=\"relatorio-" + relatorioId + ".pdf\""))
				.andReturn().getResponse().getContentAsByteArray();
		assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));

		// qualquer membro ativo tambem pode baixar — mesma regra de leitura do relatorio (RF-04.4)
		mockMvc.perform(get("/api/relatorios/" + relatorioId + "/pdf")
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isOk());
	}

	@Test
	void baixarPdfDeRelatorioInexistenteDevolveErro() throws Exception {
		String token = cadastrarELogar("Gestor Pdf Erro", "gestor.pdferro@teste.com");
		mockMvc.perform(get("/api/relatorios/999999/pdf")
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("Relatorio nao encontrado."));
	}

}
