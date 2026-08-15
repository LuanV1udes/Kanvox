package br.edu.fatec.kanvox.controlador;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** Testes dos anexos nas tarefas (RF-07.2) — a devolutiva em arquivo. */
@SpringBootTest
@AutoConfigureMockMvc
class AnexoControladorTestes {

	@Autowired
	private MockMvc mockMvc;

	private long cadastrar(String nome, String email) throws Exception {
		String resposta = mockMvc.perform(post("/api/autenticacao/cadastro")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"Senha123!\"}"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	private String logar(String email) throws Exception {
		String resposta = mockMvc.perform(post("/api/autenticacao/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"senha\":\"Senha123!\"}"))
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

	private void convidarEAceitar(String tokenGestor, long projetoId, String email, String papel,
			String tokenConvidado) throws Exception {
		mockMvc.perform(post("/api/projetos/" + projetoId + "/membros")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"papel\":\"" + papel + "\"}"))
				.andExpect(status().isCreated());
		String convites = mockMvc.perform(get("/api/convites")
				.header("Authorization", "Bearer " + tokenConvidado))
				.andReturn().getResponse().getContentAsString();
		long conviteId = ((Number) JsonPath.read(convites, "$[0].id")).longValue();
		mockMvc.perform(post("/api/convites/" + conviteId + "/aceitar")
				.header("Authorization", "Bearer " + tokenConvidado))
				.andExpect(status().isOk());
	}

	private long criarTarefa(String tokenGestor, long projetoId, long responsavelId) throws Exception {
		String resposta = mockMvc.perform(post("/api/projetos/" + projetoId + "/tarefas")
				.header("Authorization", "Bearer " + tokenGestor)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Tarefa com anexo\",\"responsaveis\":[{\"id\":" + responsavelId + "}]}"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(resposta, "$.id")).longValue();
	}

	@Test
	void membroAnexaDevolutivaEGestorBaixaOMesmoArquivo() throws Exception {
		cadastrar("Gestor Anexo", "gestor.anexo@teste.com");
		long membroId = cadastrar("Membro Anexo", "membro.anexo@teste.com");
		String tokenGestor = logar("gestor.anexo@teste.com");
		String tokenMembro = logar("membro.anexo@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Anexos");
		convidarEAceitar(tokenGestor, projetoId, "membro.anexo@teste.com", "MEMBRO", tokenMembro);
		long tarefaId = criarTarefa(tokenGestor, projetoId, membroId);

		byte[] conteudoDoArquivo = "conteudo da entrega do membro".getBytes();
		MockMultipartFile arquivo = new MockMultipartFile("arquivo", "entrega.txt",
				"text/plain", conteudoDoArquivo);

		// o membro anexa a entrega na propria tarefa
		mockMvc.perform(multipart("/api/tarefas/" + tarefaId + "/anexos")
				.file(arquivo)
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nome").value("entrega.txt"))
				.andExpect(jsonPath("$.tamanho").value(conteudoDoArquivo.length))
				// os bytes nunca aparecem no JSON
				.andExpect(jsonPath("$.dados").doesNotExist());

		// o gestor ve a lista e baixa o arquivo identico ao enviado
		String lista = mockMvc.perform(get("/api/tarefas/" + tarefaId + "/anexos")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andReturn().getResponse().getContentAsString();
		long anexoId = ((Number) JsonPath.read(lista, "$[0].id")).longValue();

		byte[] baixado = mockMvc.perform(get("/api/anexos/" + anexoId + "/download")
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"entrega.txt\""))
				.andReturn().getResponse().getContentAsByteArray();
		assertArrayEquals(conteudoDoArquivo, baixado);
	}

	@Test
	void membroNaoAnexaEmTarefaDosOutros() throws Exception {
		long gestorId = cadastrar("Gestor AlheioAnexo", "gestor.alheioanexo@teste.com");
		cadastrar("Membro AlheioAnexo", "membro.alheioanexo@teste.com");
		String tokenGestor = logar("gestor.alheioanexo@teste.com");
		String tokenMembro = logar("membro.alheioanexo@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Anexo Alheio");
		convidarEAceitar(tokenGestor, projetoId, "membro.alheioanexo@teste.com", "MEMBRO", tokenMembro);
		long tarefaDoGestor = criarTarefa(tokenGestor, projetoId, gestorId);

		MockMultipartFile arquivo = new MockMultipartFile("arquivo", "invasao.txt",
				"text/plain", "arquivo".getBytes());
		mockMvc.perform(multipart("/api/tarefas/" + tarefaDoGestor + "/anexos")
				.file(arquivo)
				.header("Authorization", "Bearer " + tokenMembro))
				.andExpect(status().isForbidden());
	}

	@Test
	void arquivoAcimaDe10MBERejeitado() throws Exception {
		long gestorId = cadastrar("Gestor Grande", "gestor.grande@teste.com");
		String tokenGestor = logar("gestor.grande@teste.com");
		long projetoId = criarProjeto(tokenGestor, "Projeto Arquivo Grande");
		long tarefaId = criarTarefa(tokenGestor, projetoId, gestorId);

		byte[] arquivoGrande = new byte[10 * 1024 * 1024 + 1];
		mockMvc.perform(multipart("/api/tarefas/" + tarefaId + "/anexos")
				.file(new MockMultipartFile("arquivo", "grande.bin", "application/octet-stream", arquivoGrande))
				.header("Authorization", "Bearer " + tokenGestor))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erro").value("O arquivo excede o limite de 10MB."));
	}

}
